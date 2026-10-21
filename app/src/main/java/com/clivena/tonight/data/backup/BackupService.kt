package com.clivena.tonight.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.clivena.tonight.BuildConfig
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.local.AppDatabase
import com.clivena.tonight.data.local.ExpenseEntity
import com.clivena.tonight.data.local.JournalEntity
import com.clivena.tonight.data.local.TaskEntity
import com.clivena.tonight.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 备份的读写编排。
 *
 * ##### 一次导出做的事
 *
 * 三张表全量 → 内存里的 [BackupPayload]；被引用到的图片 → 文件列表；
 * 两者打包写成 `cacheDir` 里的一个 zip；最后由调用方通过系统文件选择器
 * 把它交给用户选定的位置（`ACTION_CREATE_DOCUMENT`）。
 *
 * **全程不申请任何权限** —— 写出去的位置由用户在用系统选择器那一刻授权，
 * 这个 App 至今没有一条 `uses-permission`。
 *
 * ##### 一次恢复做的事
 *
 * 用户选中的 uri → 先复制到 cacheDir（只读一次系统流）→ [BackupReader.read]
 * 解析出摘要给用户看 → **用户点了确认**才 [apply]。
 * `apply` 的顺序是：图片先落盘 → 数据库一个事务写完 → 配置最后应用。
 *
 * ##### 为什么恢复只有「合并」没有「覆盖」
 *
 * 覆盖是一个不可撤销的写操作，而它对应的场景（「我只想要备份里的那份」）
 * 完全可以靠「先导出当前数据再恢复」达到，代价是两步，收益是删不掉的东西。
 * 一个自用 App 里不值得为了省一步留一条能清空全部数据、且没有回收站的路径。
 * 合并的判重键见 [taskKey] / [expenseKey]。
 */
class BackupService(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {

    /** 用 applicationContext：这个对象活得比任何一个 Activity 都久 */
    private val context = context.applicationContext

    private val themeDir: File get() = File(context.filesDir, ImageStore.DIR_THEME)
    private val journalDir: File get() = File(context.filesDir, ImageStore.DIR_JOURNAL)

    // ── 导出 ────────────────────────────────────────────────

    /**
     * 打包到缓存目录，返回待写出的文件。
     *
     * 先落成文件、再让用户选位置 —— 顺序不能反：
     * 系统文件选择器一返回就意味着「用户已经选定目的地」，
     * 那一刻要是才开始查数据库、读图片，界面会在选择器后面空转好几秒。
     */
    suspend fun createPackage(): File = withContext(Dispatchers.IO) {
        val payload = BackupPayload(
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            settings = SettingsSnapshot(
                palette = settings.paletteName.value,
                nightMode = settings.nightMode.value,
                backgroundDim = settings.backgroundDim.value,
                background = settings.backgroundName.value,
                tags = settings.tags.value
            ),
            tasks = db.taskDao().getAll().map {
                BackupTask(
                    planDate = it.planDate,
                    content = it.content,
                    orderIndex = it.orderIndex,
                    status = it.status,
                    createdAt = it.createdAt,
                    completedAt = it.completedAt,
                    updatedAt = it.updatedAt
                )
            },
            expenses = db.expenseDao().getAll().map {
                BackupExpense(
                    date = it.date,
                    amountCents = it.amountCents,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            journal = db.journalDao().getAll().map {
                BackupJournal(
                    date = it.date,
                    content = it.content,
                    mood = it.mood,
                    imageName = it.imageName,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
        )

        val target = File(context.cacheDir, suggestedFileName())
        BackupWriter.write(target, payload, collectImages(payload))
        target
    }

    /**
     * 只带**被引用到的**图片。
     *
     * 目录里可能有换背景图时没删干净的旧文件（v0.6 起换图会删旧的，
     * 但手动装过来的、恢复失败留下的都可能存在）。全目录打包等于
     * 把一堆没人看的大图塞进备份，几年下来能到几个 GB。
     */
    private fun collectImages(payload: BackupPayload): List<ImageFile> {
        val result = ArrayList<ImageFile>()
        // 按 zip 内路径去重，不按文件名：背景图和日记图在两个目录里，
        // 万一撞了同名，按文件名去重会静默丢掉后一张
        val seen = HashSet<String>()

        payload.settings?.background?.let { name ->
            val entry = BackupFormat.DIR_THEME + name
            File(themeDir, name).takeIf { it.exists() && seen.add(entry) }?.let {
                result += ImageFile(entry, it)
            }
        }
        payload.journal.forEach { entry ->
            val name = entry.imageName ?: return@forEach
            val path = BackupFormat.DIR_JOURNAL + name
            File(journalDir, name).takeIf { it.exists() && seen.add(path) }?.let {
                result += ImageFile(path, it)
            }
        }
        return result
    }

    /** 系统文件选择器里预填的名字。带日期，用户自己攒几个备份也分得清 */
    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
        return "今晚备份-${stamp.format(STAMP)}.zip"
    }

    /** 把缓存里的包写到用户选定的位置 */
    suspend fun writeTo(uri: Uri, file: File) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri)
            ?: throw BackupFormatException("写不进那个位置，换个目录再试。")
        out.use { output -> file.inputStream().buffered().use { it.copyTo(output) } }
    }

    // ── 导入 ────────────────────────────────────────────────

    /**
     * 把用户选中的文件复制进缓存并解析。
     *
     * 复制这一步是为了「只读一次系统流」：摘要要看一遍、图片还要再解一遍，
     * 同一个 `content://` 流反复打开在有些机型上会失败（对方是云盘尤其明显）。
     */
    suspend fun stage(uri: Uri): StagedBackup = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "incoming_${System.currentTimeMillis()}.zip")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw BackupFormatException("读不到这个文件，可能没有访问权限。")
            input.use { source -> file.outputStream().use { source.copyTo(it) } }
        } catch (e: BackupFormatException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            file.delete()
            throw BackupFormatException("读取文件失败：${e.message ?: "未知错误"}")
        }

        try {
            BackupReader.read(file)
        } catch (e: Exception) {
            // 解析不出来就把临时文件一起带走，别在缓存里留一份读不懂的东西
            file.delete()
            throw e
        }
    }

    /**
     * 真正写数据。**只在用户看完摘要并确认后调用。**
     *
     * 三张表在一个事务里写完：中途失败（比如内存不够）整批回滚，
     * 不会出现「任务导进去了、账目没有」这种半套状态 ——
     * 那种状态比彻底失败更难收拾，因为用户不知道该再导一次还是不该。
     */
    suspend fun apply(staged: StagedBackup): ImportResult = withContext(Dispatchers.IO) {
        val payload = staged.payload

        // 1) 图片先落盘，让后面写进去的 imageName 都有对应文件
        val imagesRestored = BackupReader.extractImages(staged.file, themeDir, journalDir)

        var tasksAdded = 0
        var tasksSkipped = 0
        var expensesAdded = 0
        var expensesSkipped = 0
        var journalAdded = 0
        var journalSkipped = 0

        db.withTransaction {
            // ── 任务 ──
            val taskKeys = db.taskDao().getAll()
                .mapTo(HashSet()) { taskKey(it.planDate, it.content, it.orderIndex, it.createdAt) }
            val newTasks = ArrayList<TaskEntity>()
            payload.tasks.forEach { t ->
                // 判重键必须是**业务字段的组合**，不能用 id：id 是本机自增的，
                // 换台机器重新分配，跨备份毫无意义
                val key = taskKey(t.planDate, t.content, t.orderIndex, t.createdAt)
                if (taskKeys.add(key)) {
                    newTasks += TaskEntity(
                        planDate = t.planDate,
                        content = t.content,
                        orderIndex = t.orderIndex,
                        status = t.status,
                        createdAt = t.createdAt,
                        completedAt = t.completedAt,
                        updatedAt = t.updatedAt
                    )
                } else {
                    tasksSkipped++
                }
            }
            if (newTasks.isNotEmpty()) db.taskDao().insertAll(newTasks)
            tasksAdded = newTasks.size

            // 两边的清单合到同一天时 orderIndex 会撞车（都有 1、2、3）。
            // 撞了就按「原来的顺序号 + 创建时间」重新排成 1..n ——
            // 两份清单各自的相对顺序都不变，只是交错在一起。
            if (newTasks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                newTasks.map { it.planDate }.distinct().forEach { day ->
                    db.taskDao().getAllOn(day).forEachIndexed { index, task ->
                        val wanted = index + 1
                        if (task.orderIndex != wanted) {
                            db.taskDao().updateOrderIndex(task.id, wanted, now)
                        }
                    }
                }
            }

            // ── 账目 ──
            val expenseKeys = db.expenseDao().getAll()
                .mapTo(HashSet()) { expenseKey(it.date, it.amountCents, it.note, it.createdAt) }
            val newExpenses = ArrayList<ExpenseEntity>()
            payload.expenses.forEach { e ->
                val key = expenseKey(e.date, e.amountCents, e.note, e.createdAt)
                if (expenseKeys.add(key)) {
                    newExpenses += ExpenseEntity(
                        date = e.date,
                        amountCents = e.amountCents,
                        note = e.note,
                        createdAt = e.createdAt,
                        updatedAt = e.updatedAt
                    )
                } else {
                    expensesSkipped++
                }
            }
            if (newExpenses.isNotEmpty()) db.expenseDao().insertAll(newExpenses)
            expensesAdded = newExpenses.size

            // ── 日记 ──
            // date 是主键，冲突是「同一天」。这时不能一律跳过：
            // 备份里那篇可能比手机上的新。取 updatedAt 大的那份。
            val existingJournal = db.journalDao().getAll().associateBy { it.date }
            val toWrite = ArrayList<JournalEntity>()
            payload.journal.forEach { j ->
                val current = existingJournal[j.date]
                if (current == null || j.updatedAt > current.updatedAt) {
                    toWrite += JournalEntity(
                        date = j.date,
                        content = j.content,
                        mood = j.mood,
                        imageName = j.imageName,
                        createdAt = j.createdAt,
                        updatedAt = j.updatedAt
                    )
                    journalAdded++
                } else {
                    journalSkipped++
                }
            }
            if (toWrite.isNotEmpty()) db.journalDao().upsertAll(toWrite)
        }

        // 3) 配置最后应用。两件事必须发生在数据库之后：
        //    背景图名要指向一个已经落盘的图片，标签要落在一次成功的导入之后。
        var settingsApplied = false
        payload.settings?.let { s ->
            val background = s.background?.takeIf { File(themeDir, it).exists() }
            settings.applySnapshot(
                palette = s.palette,
                night = s.nightMode,
                dim = s.backgroundDim,
                background = background,
                tags = s.tags
            )
            settingsApplied = true
        }

        ImportResult(
            tasksAdded = tasksAdded,
            tasksSkipped = tasksSkipped,
            expensesAdded = expensesAdded,
            expensesSkipped = expensesSkipped,
            journalAdded = journalAdded,
            journalSkipped = journalSkipped,
            imagesRestored = imagesRestored,
            settingsApplied = settingsApplied
        )
    }

    /** 用完的缓存文件一律由调用方负责带走 */
    fun discard(file: File?) {
        file?.takeIf { it.exists() }?.delete()
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

        /**
         * 任务的「同一件事」怎么算。
         *
         * 加上 content 和 orderIndex 是故意的：只用 `planDate + createdAt` 的话，
         * 一份 `createdAt` 全为 0 的老数据（v0.1 的某些记录）会把同一天的多件事
         * 判成同一件，**静默丢数据**。宁可重复也不要丢 —— 重复看得见，丢了看不见。
         */
        fun taskKey(planDate: String, content: String, orderIndex: Int, createdAt: Long) =
            "$planDate|$content|$orderIndex|$createdAt"

        fun expenseKey(date: String, amountCents: Long, note: String, createdAt: Long) =
            "$date|$amountCents|$note|$createdAt"
    }
}
