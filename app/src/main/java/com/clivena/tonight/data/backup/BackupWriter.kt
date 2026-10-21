package com.clivena.tonight.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 一张要进 zip 的图片。[entryName] 是 zip 内部路径，必须带 `images/...` 前缀 */
internal data class ImageFile(val entryName: String, val file: File)

/**
 * 把一份数据写成 zip。
 *
 * ##### 为什么是 zip 而不是一个大 json
 *
 * 图片转 base64 塞进 json 会让体积膨胀三分之一，更要紧的是**读的时候要整个进内存**：
 * 一百张 2560 长边的日记配图，解码那一刻就是几十 MB 的字符串 —— 这正是
 * 这个工程一直在躲的那种崩法（见 [com.clivena.tonight.util.ImageStore] 的注释）。
 * zip 是一个条目一个条目流式写、流式读的，同样的内容内存占用是个位数 MB。
 *
 * ##### zip 里为什么同时放 json 和 csv
 *
 * `data.json` 是给 App 自己读回去的，`*.csv` 是给人看的 ——
 * 后者不需要 App 也能打开，丢到 Excel 里就能核对。两者同源同一次导出，
 * 不存在「导出的表格和备份对不上」这种可能。
 */
internal object BackupWriter {

    fun write(target: File, payload: BackupPayload, images: List<ImageFile>) {
        ZipOutputStream(target.outputStream().buffered(), Charsets.UTF_8).use { zip ->
            zip.putText(BackupFormat.ENTRY_DATA, json(payload))
            zip.putText(BackupFormat.ENTRY_TASKS_CSV, tasksCsv(payload.tasks))
            zip.putText(BackupFormat.ENTRY_EXPENSES_CSV, expensesCsv(payload.expenses))
            zip.putText(BackupFormat.ENTRY_JOURNAL_CSV, journalCsv(payload.journal))

            images.forEach { image ->
                zip.putNextEntry(ZipEntry(image.entryName))
                image.file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ── data.json ────────────────────────────────────────────

    private fun json(p: BackupPayload): String {
        val root = JSONObject()
        // 前三项是「身份」：导入时先认这个，认不出来就不往下读
        root.put("format", BackupFormat.NAME)
        root.put("formatVersion", BackupFormat.VERSION)
        root.put("appVersion", p.appVersion)
        root.put("exportedAt", p.exportedAt)
        // 多存一份人能读的时间。用它算日期的代码永远不会去解析这个字段，
        // 它存在的意义只是「用文本编辑器打开也能看懂这是哪天的备份」
        root.put("exportedAtText", clock(p.exportedAt))

        p.settings?.let { s ->
            root.put(
                "settings",
                JSONObject().apply {
                    put("palette", s.palette)
                    put("nightMode", s.nightMode)
                    put("backgroundDim", s.backgroundDim.toDouble())
                    putOrNull("background", s.background)
                    put("tags", JSONArray().apply { s.tags.forEach { put(it) } })
                }
            )
        }

        root.put(
            "tasks",
            JSONArray().apply {
                p.tasks.forEach { t ->
                    put(
                        JSONObject().apply {
                            put("planDate", t.planDate)
                            put("content", t.content)
                            put("orderIndex", t.orderIndex)
                            put("status", t.status)
                            put("createdAt", t.createdAt)
                            putOrNull("completedAt", t.completedAt)
                            put("updatedAt", t.updatedAt)
                        }
                    )
                }
            }
        )

        root.put(
            "expenses",
            JSONArray().apply {
                p.expenses.forEach { e ->
                    put(
                        JSONObject().apply {
                            put("date", e.date)
                            put("amountCents", e.amountCents)
                            put("note", e.note)
                            put("createdAt", e.createdAt)
                            put("updatedAt", e.updatedAt)
                        }
                    )
                }
            }
        )

        root.put(
            "journal",
            JSONArray().apply {
                p.journal.forEach { j ->
                    put(
                        JSONObject().apply {
                            put("date", j.date)
                            put("content", j.content)
                            putOrNull("mood", j.mood)
                            putOrNull("imageName", j.imageName)
                            put("createdAt", j.createdAt)
                            put("updatedAt", j.updatedAt)
                        }
                    )
                }
            }
        )

        // 缩进 2 格：几千条也就是几百 KB，换来的是「这个文件能被人打开看懂」
        return root.toString(2)
    }

    // ── csv ─────────────────────────────────────────────────

    /**
     * 表头用中文。
     *
     * 因为 csv 这一半的读者是人 —— data.json 已经是机器读的了，
     * 两个都写成英文键名等于做了两遍同一件事。
     */
    private fun tasksCsv(rows: List<BackupTask>): String = buildString {
        append(BOM)
        line("日期", "顺序", "内容", "状态", "完成时间", "创建时间")
        rows.forEach { t ->
            line(
                t.planDate,
                t.orderIndex.toString(),
                t.content,
                statusText(t.status),
                t.completedAt?.let(::clock).orEmpty(),
                clock(t.createdAt)
            )
        }
    }

    private fun expensesCsv(rows: List<BackupExpense>): String = buildString {
        append(BOM)
        line("日期", "金额(元)", "备注", "记录时间")
        rows.forEach { e ->
            // 金额这一列是给人看的，所以是元；但要**从分精确换算**，
            // 不能先转 Double 再格式化 —— 那等于在钱的路径上又放回一个浮点数
            line(e.date, yuan(e.amountCents), e.note, clock(e.createdAt))
        }
    }

    private fun journalCsv(rows: List<BackupJournal>): String = buildString {
        append(BOM)
        line("日期", "心情", "正文", "配图", "更新时间")
        rows.forEach { j ->
            line(
                j.date,
                j.mood?.toString().orEmpty(),
                j.content,
                j.imageName.orEmpty(),
                clock(j.updatedAt)
            )
        }
    }

    /**
     * 一个单元格。
     *
     * 只有三种情况必须加引号：里面有逗号、有引号、有换行 ——
     * 日记正文里这三种都是常态，不处理的话一行就串成好几列，引号也没法转义。
     * 另外「内容/备注」这类文本列如果是纯数字（比如备注写成「30」），
     * 也加引号，否则 Excel 会把它当数字右对齐、还参与求和。
     */
    private fun csvCell(value: String, quote: Boolean): String =
        if (quote) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun needsQuote(raw: String): Boolean =
        raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }

    private fun StringBuilder.line(vararg cells: String) {
        cells.forEachIndexed { i, raw ->
            if (i > 0) append(',')
            // 前三列（日期 / 顺序或金额 / 心情）是机器生成的裸值，
            // 从第三列起才是用户打的字
            val quote = needsQuote(raw) || (i >= 2 && raw.matches(NUMERIC_LIKE))
            append(csvCell(raw, quote))
        }
        append(CRLF)
    }

    private val NUMERIC_LIKE = Regex("""^-?\d+(\.\d+)?$""")

    /** UTF-8 BOM。没有它，Windows 上的 Excel 会用 GBK 解 UTF-8，中文全是乱码 */
    private const val BOM = "\uFEFF"
    private const val CRLF = "\r\n"

    private fun statusText(raw: String): String = when (raw) {
        "DONE" -> "已完成"
        "DROPPED" -> "已删除"
        else -> "待做"
    }

    // ── 工具 ────────────────────────────────────────────────

    /**
     * 分 → 元，纯整数运算。
     *
     * `cents / 100.0` 看着更短，但那是把钱的路径又交回给浮点数一次 ——
     * 这个工程从注释到类型都在守这条线，不该在导出这里破例。
     */
    private fun yuan(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = kotlin.math.abs(cents)
        val fen = (abs % 100).toString().padStart(2, '0')
        return "$sign${abs / 100}.$fen"
    }

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun clock(millis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(CLOCK)

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}

/** `JSONObject.put(key, null)` 会把键删掉，可空字段得显式写 NULL 才留得住 */
private fun JSONObject.putOrNull(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}
