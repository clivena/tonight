package com.clivena.tonight.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.clivena.tonight.TonightApp
import com.clivena.tonight.domain.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 四张卡片共同的那部分：广播接收、勾选、以及「这张卡长什么样」的三个声明。
 *
 * 所有事情都从 [onReceive] 走，不走 `onUpdate`。原因是组件被**拉大拉小**时
 * 也要重画（行数跟着变），而 `onUpdate` 只在添加和定时刷新时触发；
 * `onReceive` 能看到全部动作，一处判断比两处各写一半更不容易漏。
 *
 * 每个分支都必须 `goAsync()`：广播接收方只有十来秒寿命，而读数据库 +
 * 重建 RemoteViews 是异步的。不 goAsync 的话进程可能在读库读到一半被回收 ——
 * 表现是「点了一下没反应，偶尔又反应了」。
 *
 * 勾选的 Intent 指向**触发它的那张卡的 provider 类**（见 WidgetRenderer.toggleIntent），
 * 所以这里处理 ACTION_TOGGLE_TASK 是必需的，不能挪到单独的 receiver ——
 * 那样 v0.11 已经添加到桌面的旧卡片（PendingIntent 里写死了 TonightWidgetProvider）
 * 点圈会彻底没反应，而且用户会以为是自己点歪了。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    /** 这张卡的布局。合并卡两张共用一个，靠 match_parent 自适应宽度 */
    abstract val layoutRes: Int

    /**
     * 这张卡显示多少行任务。
     *
     * 返回 **0 表示这张卡没有任务区** —— 渲染层据此决定要不要碰 `task_label` /
     * `task_rows` 这些 id。用 0 而不是另外加一个布尔开关：
     * 一个卡片的「有没有某块区域」和「这块区域放几行」本来就是同一件事，
     * 拆成两个字段就会出现「说有区域但行数是 0」这种自相矛盾的状态。
     */
    abstract fun taskCap(heightDp: Int): Int

    /** 这张卡显示几笔账。0 表示没有记账区 */
    abstract fun moneyCap(heightDp: Int): Int

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_TASK -> runAsync(context) {
                toggle(context, intent.getLongExtra(EXTRA_TASK_ID, -1L))
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> runAsync(context) {
                WidgetRefresher.refreshProviderNow(context, javaClass)
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
        // 仓储写完会通过 InvalidationTracker 再触发一次刷新，但那是异步的、时机不定。
        // 这里主动刷一次，保证「点完就变」；而且是刷**全部**卡片，
        // 因为同一条任务可能同时出现在三张卡上。
        WidgetRefresher.refreshAllNow(context)
    }

    companion object {

        const val ACTION_TOGGLE_TASK = "com.clivena.tonight.widget.TOGGLE_TASK"
        const val EXTRA_TASK_ID = "taskId"
    }
}
