package com.clivena.tonight.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.clivena.tonight.TonightApp
import com.clivena.tonight.domain.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小组件的入口。
 *
 * 所有事情都从 [onReceive] 走，不走 `onUpdate`。
 * 原因是这一版要在组件**被拉大拉小**时也重画（行数跟着变），
 * 而 `onUpdate` 只在添加和定时刷新时触发；`onReceive` 能看到全部动作，
 * 一处判断比两处各写一半更不容易漏。
 *
 * 每个分支都必须 `goAsync()`：广播的接收方只有十来秒寿命，
 * 而读数据库 + 重建 RemoteViews 是异步的。不 goAsync 的话，
 * 进程可能在读库读到一半被回收 —— 表现是"点了一下没反应，偶尔又反应了"。
 */
class TonightWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_TASK -> runAsync(context) {
                toggle(context, intent.getLongExtra(EXTRA_TASK_ID, -1L))
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> runAsync(context) {
                refreshAllNow(context)
            }

            else -> super.onReceive(context, intent)
        }
    }

    private fun runAsync(context: Context, block: suspend (Context) -> Unit) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block(appContext)
            } finally {
                // 不 finish 的话系统会一直认为这个广播没处理完，
                // 连续点几次之后就不再派发了
                pending.finish()
            }
        }
    }

    /**
     * 在桌面上勾掉 / 撤销一条任务。
     *
     * 调用的是仓储层的 `complete` / `reopen`，不是直接 UPDATE 数据库 ——
     * 那样会绕过 `completedAt` 和 `updatedAt` 的维护，
     * 导出的备份里就会出现「已勾选但没有完成时间」的记录。
     */
    private suspend fun toggle(context: Context, taskId: Long) {
        if (taskId <= 0L) return
        val app = context.applicationContext as? TonightApp ?: return
        val task = app.database.taskDao().getById(taskId) ?: return

        if (task.status == TaskStatus.DONE.name) {
            app.repository.reopen(taskId)
        } else {
            app.repository.complete(taskId)
        }
        // 仓储写完会通过 InvalidationTracker 再触发一次刷新，
        // 但那是异步的、时机不定。这里主动刷一次，保证「点完就变」。
        refreshAllNow(context)
    }

    companion object {

        const val ACTION_TOGGLE_TASK = "com.clivena.tonight.widget.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "taskId"

        /**
         * 从任意地方触发刷新（App 内改了数据时用）。
         *
         * 桌面上一个组件都没有时直接返回 —— 那样连数据库都不用开。
         */
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            if (manager.getAppWidgetIds(component(appContext)).isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch { refreshAllNow(appContext) }
        }

        suspend fun refreshAllNow(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(component(context)).forEach { id ->
                renderWidget(context, manager, id)
            }
        }

        private fun component(context: Context) =
            ComponentName(context, TonightWidgetProvider::class.java)
    }
}
