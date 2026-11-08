package com.clivena.tonight

import android.app.Application
import androidx.room.InvalidationTracker
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.data.JournalRepository
import com.clivena.tonight.data.LocalExpenseRepository
import com.clivena.tonight.data.LocalJournalRepository
import com.clivena.tonight.data.LocalTaskRepository
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.data.backup.BackupService
import com.clivena.tonight.data.local.AppDatabase
import com.clivena.tonight.widget.TonightWidgetProvider

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

    override fun onCreate() {
        super.onCreate()
        watchForWidgetRefresh()
    }

    /**
     * 数据一变就刷新桌面小组件。
     *
     * **只写这一处**，而不是在每个 Repository 的写方法后面挂一行。
     * 那种写法要记得改七处，而"忘了某一条写路径"的表现是
     * 「加了任务桌面没变」—— 而这恰恰是小组件最容易让人失望的地方：
     * 它看起来该实时，却不会自己发现数据变了。
     *
     * Room 的 InvalidationTracker 在事务提交后回调，覆盖了所有写入
     * （包括以后新增的、以及备份恢复这种一次写三张表的批量操作）。
     *
     * 回调发生在 Room 的查询线程上，所以这里只做一次轻量的调度，
     * 真正的读库和重建 RemoteViews 在 [TonightWidgetProvider.refreshAll] 里。
     */
    private fun watchForWidgetRefresh() {
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("tasks", "expenses", "journal") {
                override fun onInvalidated(tables: Set<String>) {
                    TonightWidgetProvider.refreshAll(this@TonightApp)
                }
            }
        )
    }
}
