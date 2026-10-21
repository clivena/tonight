package com.clivena.tonight.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * 读一个备份 zip。
 *
 * ##### 全部读取都用 [ZipFile] 而不是 ZipInputStream
 *
 * 因为 ZipInputStream 只顺序读、而且从本地文件头拿不到真实的解压后大小
 * （条目大小可能是 -1）。而「先看这份备份有多大」恰恰是判断
 * 「这是不是一份能让我手机存满的文件」的唯一依据 —— 拿不到就没法设上限。
 * ZipFile 读的是中央目录，大小是准的，还能按名字二次打开同一份文件。
 *
 * ##### 一句话原则：**先全部校验，再动一个字节**
 *
 * [read] 只碰文件、不碰数据库、不碰图片目录。它只有两个结果：
 * 返回一份已经在内存里的数据，或者抛一句能给人看的话。
 * 「写」这件事全部发生在用户看完摘要点确认之后（[BackupService.apply]）。
 */
internal object BackupReader {

    /**
     * 解析并生成摘要。
     *
     * @throws BackupFormatException 不是 zip、不是本 App 的备份、格式版本过新、体积超限
     */
    fun read(file: File): StagedBackup {
        if (!file.exists() || file.length() == 0L) {
            throw BackupFormatException("这个文件是空的。")
        }

        var hasData = false
        var imageCount = 0

        try {
            ZipFile(file).use { zip ->
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    total += entry.size.coerceAtLeast(0)
                    if (total > BackupFormat.MAX_TOTAL_BYTES) {
                        throw BackupFormatException(
                            "这个压缩包解开后超过 1.5 GB，不像是「今晚」的备份。"
                        )
                    }
                    if (entry.name == BackupFormat.ENTRY_DATA) {
                        hasData = true
                    } else if (!entry.isDirectory && isImage(entry.name)) {
                        imageCount++
                    }
                }

                if (!hasData) {
                    throw BackupFormatException(
                        "包里没有 data.json —— 这不是「今晚」导出的备份。"
                    )
                }
            }
        } catch (e: ZipException) {
            throw BackupFormatException("这个文件不是 zip 压缩包，或者已经损坏。")
        }

        val raw = try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(BackupFormat.ENTRY_DATA)
                    ?: throw BackupFormatException("包里没有 data.json。")
                if (entry.size > BackupFormat.MAX_DATA_BYTES) {
                    throw BackupFormatException("data.json 异常大，不像是「今晚」的备份。")
                }
                zip.getInputStream(entry).buffered().use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: ZipException) {
            throw BackupFormatException("这个文件不是 zip 压缩包，或者已经损坏。")
        }

        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw BackupFormatException("data.json 不是有效的 JSON，文件可能已损坏。")
        }

        if (root.optString("format") != BackupFormat.NAME) {
            throw BackupFormatException("这不是「今晚」的备份文件。")
        }
        val formatVersion = root.optInt("formatVersion", 0)
        if (formatVersion <= 0) {
            throw BackupFormatException("备份缺少格式版本号，无法判断怎么读。")
        }
        if (formatVersion > BackupFormat.VERSION) {
            throw BackupFormatException(
                "这份备份来自更新版本的 App（格式 v$formatVersion），" +
                    "当前版本只认到 v${BackupFormat.VERSION}。请先更新 App。"
            )
        }

        val payload = BackupPayload(
            appVersion = root.optString("appVersion", "未知"),
            exportedAt = root.optLong("exportedAt", 0L),
            settings = root.optJSONObject("settings")?.let(::parseSettings),
            tasks = parseArray(root.optJSONArray("tasks")) { o ->
                val date = o.optString("planDate")
                if (!isDate(date)) return@parseArray null
                BackupTask(
                    planDate = date,
                    content = o.optString("content"),
                    orderIndex = o.optInt("orderIndex", 0),
                    status = o.optString("status", "PENDING"),
                    createdAt = o.optLong("createdAt", 0L),
                    completedAt = o.optLongOrNull("completedAt"),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            },
            expenses = parseArray(root.optJSONArray("expenses")) { o ->
                val date = o.optString("date")
                if (!isDate(date)) return@parseArray null
                BackupExpense(
                    date = date,
                    amountCents = o.optLong("amountCents", 0L),
                    note = o.optString("note"),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            },
            journal = parseArray(root.optJSONArray("journal")) { o ->
                val date = o.optString("date")
                if (!isDate(date)) return@parseArray null
                BackupJournal(
                    date = date,
                    content = o.optString("content"),
                    mood = o.optIntOrNull("mood"),
                    // 只留文件名。就算有人在 zip 里塞了 ../ 也走不出去
                    imageName = o.optString("imageName").takeIf { it.isNotBlank() }?.let(::baseName),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        )

        return StagedBackup(
            file = file,
            payload = payload,
            summary = BackupSummary(
                exportedAt = payload.exportedAt,
                appVersion = payload.appVersion,
                formatVersion = formatVersion,
                taskCount = payload.tasks.size,
                expenseCount = payload.expenses.size,
                journalCount = payload.journal.size,
                imageCount = imageCount,
                hasSettings = payload.settings != null
            )
        )
    }

    /**
     * 把 zip 里的图片写到私有目录。返回写成功的张数。
     *
     * 图片先落盘、数据库后写 —— 顺序是刻意的：
     * 反过来（先写库后写图）一旦中途失败，日记里就会留下指向不存在文件的 imageName，
     * 界面上表现为一个永远空着的图位。现在这个顺序失败时最多留几个没人引用的孤儿文件，
     * 用户看不见，也不影响任何功能。
     */
    fun extractImages(file: File, themeDir: File, journalDir: File): Int {
        var count = 0
        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val target = when {
                        entry.name.startsWith(BackupFormat.DIR_THEME) -> themeDir
                        entry.name.startsWith(BackupFormat.DIR_JOURNAL) -> journalDir
                        else -> null
                    } ?: continue

                    val name = baseName(entry.name) ?: continue
                    if (!target.exists()) target.mkdirs()
                    val dest = File(target, name)
                    // 先写临时文件再改名：中途失败不会留下半张图，
                    // 而半张图比没图更糟 —— 它会被当成有效文件解码失败
                    val temp = File(target, "$name.part")
                    zip.getInputStream(entry).use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (temp.length() > 0) {
                        dest.delete()
                        temp.renameTo(dest)
                        count++
                    } else {
                        temp.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 同一张图写不进去不该让整次导入失败：文字数据已经到手了，
            // 少几张图是这个结果里最轻的一种
        }
        return count
    }

    // ── 内部 ────────────────────────────────────────────────

    private fun isImage(name: String): Boolean =
        name.startsWith(BackupFormat.DIR_THEME) || name.startsWith(BackupFormat.DIR_JOURNAL)

    /**
     * 取路径最后一段。
     *
     * 备份里的图片名只该是个文件名。这里强制剥掉目录部分，
     * 顺带把 `.` / `..` / 空名挡掉 —— zip 里的条目名是外部可控的，
     * 拼接之前必须先降级成「一个文件名」。
     */
    private fun baseName(raw: String): String? {
        val name = raw.substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.length > 120) return null
        return name
    }

    private fun parseSettings(o: JSONObject) = SettingsSnapshot(
        palette = o.optString("palette"),
        nightMode = o.optBoolean("nightMode", false),
        backgroundDim = o.optDouble("backgroundDim", 0.88).toFloat(),
        background = o.optString("background").takeIf { it.isNotBlank() }?.let(::baseName),
        tags = o.optJSONArray("tags")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val tag = array.optString(i, "").trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        } ?: emptyList()
    )

    /**
     * 一行都解析不出来就抛，而不是返回空列表。
     *
     * 「备份里有 tasks 这个数组但每一条都读不懂」和「备份里根本没有任务」
     * 是两回事，前者说明文件有问题，不该被当成「这份备份是空的」静默通过。
     */
    private fun <T> parseArray(array: JSONArray?, parse: (JSONObject) -> T?): List<T> {
        if (array == null) return emptyList()
        val result = ArrayList<T>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            parse(o)?.let { result.add(it) }
        }
        return result
    }

    /** yyyy-MM-dd。格式不对的行直接丢 —— 它进来也显示不出来，只会在界面上变成一个坏格子 */
    private fun isDate(raw: String): Boolean = DATE.matches(raw)

    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key, 0L)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, 0)
}
