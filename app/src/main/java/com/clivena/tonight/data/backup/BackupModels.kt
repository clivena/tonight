package com.clivena.tonight.data.backup

/**
 * 备份文件的格式定义。
 *
 * ##### 为什么不直接序列化 Entity
 *
 * 这几个 DTO 刻意和 [com.clivena.tonight.data.local] 里的 Entity 长得像但**不是同一个类**。
 * 原因是两者的变更理由完全不同：
 *   · Entity 会因为「界面上要多存一个字段」而改；
 *   · 备份格式只能因为「格式本身要升级」而改。
 * 绑在一起的话，某天给 tasks 加一列，旧备份就跟着读不出来 ——
 * 而备份恰恰是「格式必须比代码活得久」的那类东西。
 *
 * 同理，这里**没有 id**：自增 id 是本机的行号，换台机器就重新分配，
 * 带进备份只会让人以为它有什么含义。跨备份的判重靠业务键（见 [BackupService]）。
 */
data class BackupTask(
    /** yyyy-MM-dd */
    val planDate: String,
    val content: String,
    val orderIndex: Int,
    /** PENDING / DONE / DROPPED */
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val updatedAt: Long
)

data class BackupExpense(
    /** yyyy-MM-dd */
    val date: String,
    /** 分。备份里也是分 —— 元只在输入和显示两个端点出现，中间层不许换算 */
    val amountCents: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupJournal(
    /** yyyy-MM-dd，一天一篇 */
    val date: String,
    val content: String,
    val mood: Int?,
    /** 文件名，不是路径。图片本身在 zip 里 */
    val imageName: String?,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 外观设置。
 *
 * 单独一个可空字段而不是散在顶层：一台机器换到另一台时，
 * 「数据要」和「样子要不要一起搬」是两个决定，格式上留出这个缝，
 * 以后想只搬数据不用改结构。
 */
data class SettingsSnapshot(
    /** 色板枚举名，认不出来就退回默认 */
    val palette: String,
    val nightMode: Boolean,
    /** 遮罩 alpha（越大越淡），不是界面上那个「清晰度」 */
    val backgroundDim: Float,
    val background: String?,
    val tags: List<String>
)

/** data.json 的全部内容 */
data class BackupPayload(
    val appVersion: String,
    val exportedAt: Long,
    val settings: SettingsSnapshot?,
    val tasks: List<BackupTask>,
    val expenses: List<BackupExpense>,
    val journal: List<BackupJournal>
)

/**
 * 预览摘要 —— 导入前只给用户看这个，不给按钮后面接一堆看不见的东西。
 *
 * 用户在这一屏做的判断是「这份备份是我想恢复的那份吗」，
 * 所以四个数字缺一不可：只有「共 3 项」这种概括没法判断。
 */
data class BackupSummary(
    val exportedAt: Long,
    val appVersion: String,
    val formatVersion: Int,
    val taskCount: Int,
    val expenseCount: Int,
    val journalCount: Int,
    val imageCount: Int,
    val hasSettings: Boolean
) {
    val isEmpty: Boolean
        get() = taskCount == 0 && expenseCount == 0 && journalCount == 0
}

/** 导入结果。跳过数要如实报 —— 那是「这份数据本来就有」的证据，不是失败 */
data class ImportResult(
    val tasksAdded: Int,
    val tasksSkipped: Int,
    val expensesAdded: Int,
    val expensesSkipped: Int,
    val journalAdded: Int,
    val journalSkipped: Int,
    val imagesRestored: Int,
    val settingsApplied: Boolean
) {
    val totalAdded: Int get() = tasksAdded + expensesAdded + journalAdded
    val totalSkipped: Int get() = tasksSkipped + expensesSkipped + journalSkipped
}

/** 已经落到缓存目录、解析过一遍的备份。解析一次，后面所有步骤都读它 */
data class StagedBackup(
    val file: java.io.File,
    val payload: BackupPayload,
    val summary: BackupSummary
)

/**
 * 备份文件读不了。
 *
 * 单独一个类型而不是 runCatching 兜成 null：这里每一种失败
 * 都要给用户一句能看懂的话（「这不是今晚的备份文件」/「备份版本比 App 新」），
 * 而这些东西一旦被吞成 null 就只能显示「失败了」。
 */
class BackupFormatException(message: String) : Exception(message)

internal object BackupFormat {
    /** 写进 data.json，用来在导入时一眼认出「这是不是本 App 的文件」 */
    const val NAME = "tonight-backup"

    /** 格式版本。加字段可以不加版本号，改语义才加 */
    const val VERSION = 1

    const val ENTRY_DATA = "data.json"
    const val ENTRY_TASKS_CSV = "tasks.csv"
    const val ENTRY_EXPENSES_CSV = "expenses.csv"
    const val ENTRY_JOURNAL_CSV = "journal.csv"
    const val DIR_THEME = "images/theme/"
    const val DIR_JOURNAL = "images/journal/"

    /**
     * 解压总量上限。
     *
     * 不是防自己 —— 是防「随手选了个别的东西」。一个几十 GB 的压缩包
     * 会在解压时把手机存储写满，而那时候用户只会看到 App 卡死。
     */
    const val MAX_TOTAL_BYTES = 1_500L * 1024 * 1024

    /** data.json 上限。几千条记录的超大备份也就几 MB，32MB 已经非常宽松 */
    const val MAX_DATA_BYTES = 32L * 1024 * 1024
}
