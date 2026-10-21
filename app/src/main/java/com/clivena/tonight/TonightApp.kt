package com.clivena.tonight

import android.app.Application
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.data.JournalRepository
import com.clivena.tonight.data.LocalExpenseRepository
import com.clivena.tonight.data.LocalJournalRepository
import com.clivena.tonight.data.LocalTaskRepository
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.data.backup.BackupService
import com.clivena.tonight.data.local.AppDatabase

/**
 * 手动持有依赖。
 *
 * 这个规模（4 个 Repository、7 个 ViewModel）仍然不值得引入 Hilt：
 * 依赖链只有一条，手动构造更简单、编译更快、出错面更小。
 * 等真加云同步时再考虑。
 *
 * 三个数据仓储共用一个数据库实例，但各自只拿自己那张表的 DAO ——
 * 模块之间不互相看见。[SettingsRepository] 例外：它不碰数据库，
 * 只读 SharedPreferences。
 */
class TonightApp : Application() {

    /**
     * 数据库本身也暴露出来。
     *
     * 三个仓储各自只拿自己那张表的 DAO，互不相见（见下）——
     * 这个约束对界面是对的，但备份是唯一一个**天然要同时摸三张表**的地方，
     * 所以它拿的是这个实例，而不是三个 DAO。
     */
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    val repository: TaskRepository by lazy {
        LocalTaskRepository(database.taskDao())
    }

    val expenseRepository: ExpenseRepository by lazy {
        LocalExpenseRepository(database.expenseDao())
    }

    val journalRepository: JournalRepository by lazy {
        LocalJournalRepository(database.journalDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val backupService: BackupService by lazy {
        BackupService(this, database, settingsRepository)
    }
}
