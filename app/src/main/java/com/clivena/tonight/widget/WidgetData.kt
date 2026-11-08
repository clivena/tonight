package com.clivena.tonight.widget

import android.content.Context
import com.clivena.tonight.TonightApp
import com.clivena.tonight.domain.TaskStatus
import java.time.LocalDate

/** 一行任务。只带小组件要画的三个东西 */
data class WidgetTask(
    val id: Long,
    val content: String,
    val done: Boolean
)

/** 右列的一笔账 */
data class WidgetMoney(
    val note: String,
    val amountCents: Long
)

/**
 * 一次刷新的全部内容。
 *
 * [hiddenTasks] 是"没显示出来的条数"，不是"没做完的条数" ——
 * 两者在「前几件都做完了、后面还有」的时候完全不同，
 * 而这个差别决定了用户会不会以为今天只有这三件事。
 */
data class WidgetSnapshot(
    val tasks: List<WidgetTask>,
    val hiddenTasks: Int,
    val spentCents: Long,
    val hasSpending: Boolean,
    val recent: List<WidgetMoney>
)

/**
 * 读一次快照。
 *
 * 和界面里的 ViewModel 不同，这里**不订阅 Flow**：
 * 小组件每次刷新就是重画一遍，没有"界面还活着"这个概念，
 * 订阅反而要处理谁在什么时候关掉。所以 DAO 上专门加了两个 suspend 的一次性查询。
 *
 * 日期取的是**调用这一刻的今天**，而不是上次刷新时记下的那天 ——
 * 组件跨夜不会自己变，但只要它被刷新（30 分钟兜底或用户点了），
 * 就应该显示新的那一天，而不是继续显示昨天。
 */
suspend fun readWidgetSnapshot(
    context: Context,
    maxTasks: Int,
    maxMoney: Int
): WidgetSnapshot {
    val app = context.applicationContext as TonightApp
    val today = LocalDate.now().toString()

    val allTasks = app.database.taskDao().getActive(today)
    val tasks = allTasks.take(maxTasks).map { entity ->
        WidgetTask(
            id = entity.id,
            content = entity.content,
            done = entity.status == TaskStatus.DONE.name
        )
    }

    val expenses = app.database.expenseDao().getByDate(today)
    val sum = app.database.expenseDao().getSum(today) ?: 0L

    return WidgetSnapshot(
        tasks = tasks,
        hiddenTasks = (allTasks.size - tasks.size).coerceAtLeast(0),
        spentCents = sum,
        hasSpending = expenses.isNotEmpty(),
        recent = expenses.take(maxMoney).map { WidgetMoney(it.note, it.amountCents) }
    )
}
