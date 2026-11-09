package com.clivena.tonight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.clivena.tonight.MainActivity
import com.clivena.tonight.R
import com.clivena.tonight.util.MoneyUtil

/**
 * 把一份快照画成 RemoteViews 并交给桌面。
 *
 * 为什么是普通函数而不是 Composable：RemoteViews 是 launcher 进程里的
 * 一套受限 View，Compose 根本进不去。这不是风格选择，是机制边界。
 *
 * 四张卡片共用这一套渲染，靠 [BaseWidgetProvider] 给出的三件事分流：
 * 布局 id、任务行数、账目笔数。**行数返回 0 就等于「这张卡没有这块区域」**，
 * 所以这里绝不能出现「不管有没有都先 setTextColor 一下」的写法 ——
 * 对不存在的 id 调用 RemoteViews 方法不会抛在 App 这边，
 * 只会在 launcher 里 inflate 失败，桌面显示「载入窗口小插件时出现问题」。
 */

private const val PI_FLAGS =
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/** 拿不到真实尺寸时的兜底：2 格高的标准值 */
private const val DEFAULT_HEIGHT_DP = 110

/**
 * 刷新一个组件实例。
 *
 * 行数不是写死的，按**桌面给的实际高度**算 ——
 * 这是 minSdk 26 上唯一能做的「响应式」：
 * 布局不会变（那是 API 31 的 `setResponsiveEnabled`），但行数可以跟着变。
 * 用户把组件拉高，就多显示几件事，而不是把三行字拉成三行更大的字。
 */
suspend fun renderWidget(
    context: Context,
    manager: AppWidgetManager,
    appWidgetId: Int,
    provider: BaseWidgetProvider
) {
    val heightDp = manager.getAppWidgetOptions(appWidgetId)
        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_HEIGHT_DP)

    val taskCap = provider.taskCap(heightDp)
    val moneyCap = provider.moneyCap(heightDp)

    val snapshot = readWidgetSnapshot(context, taskCap, moneyCap)
    manager.updateAppWidget(
        appWidgetId,
        buildViews(context, snapshot, readWidgetColors(context), provider, taskCap, moneyCap)
    )
}

private fun buildViews(
    context: Context,
    s: WidgetSnapshot,
    c: WidgetColors,
    provider: BaseWidgetProvider,
    taskCap: Int,
    moneyCap: Int
): RemoteViews {
    val views = RemoteViews(context.packageName, provider.layoutRes)

    // 卡片底：两张 drawable 二选一。这里刻意不用 setColorFilter 染色 ——
    // 那是反射调用，某些 ROM 上不生效，而失败的样子是一块纯白卡片，
    // 夜间档会变成「白底浅字」，比不做还糟。
    views.setImageViewResource(
        R.id.widget_card,
        if (c.night) R.drawable.widget_card_dark else R.drawable.widget_card_light
    )

    val hasTasks = taskCap > 0
    val hasMoney = moneyCap > 0

    if (hasTasks) fillTasks(views, context, s, c, provider.javaClass)
    if (hasMoney) fillMoney(views, context, s, c)
    if (hasTasks && hasMoney) {
        views.setInt(R.id.divider, "setBackgroundColor", c.divider)
    }

    setClicks(views, context, hasTasks, hasMoney)
    return views
}

/**
 * 任务列。widget_today 和 widget_tonight 里这两块 id 同名，
 * 所以同一段代码服务 3×2、4×2、5×2 三种卡片。
 */
private fun fillTasks(
    views: RemoteViews,
    context: Context,
    s: WidgetSnapshot,
    c: WidgetColors,
    owner: Class<*>
) {
    views.setTextColor(R.id.task_label, c.muted)
    views.setTextViewText(R.id.task_label, labelText(context, s))

    views.removeAllViews(R.id.task_rows)
    if (s.tasks.isEmpty()) {
        views.addView(
            R.id.task_rows,
            plainRow(context, context.getString(R.string.widget_no_tasks), c.muted)
        )
    } else {
        s.tasks.forEach { views.addView(R.id.task_rows, taskRow(context, it, c, owner)) }
    }
}

/** 记账区：金额 + 若干笔明细。2×2 和合并卡共用 */
private fun fillMoney(
    views: RemoteViews,
    context: Context,
    s: WidgetSnapshot,
    c: WidgetColors
) {
    views.setTextColor(R.id.money_label, c.muted)
    views.setTextColor(R.id.money_sum, c.accent)
    views.setTextViewText(R.id.money_sum, MoneyUtil.format(s.spentCents))

    views.removeAllViews(R.id.money_rows)
    if (s.hasSpending) {
        s.recent.forEach { views.addView(R.id.money_rows, moneyRow(context, it, c)) }
    } else {
        views.addView(
            R.id.money_rows,
            plainRow(context, context.getString(R.string.widget_no_spending), c.muted)
        )
    }
}

/**
 * 点击落点。
 *
 * 合并卡：点左边进「今天」、点右边进「记账」——
 * 两个落点按区域分，因为一块卡上确实有两件事。
 * 单卡：整张卡只有一个去处，给根布局设一次就够。
 *
 * 注意顺序：先给 root 设，再给 money 区设。RemoteViews 里子 View 的点击
 * 会先被消费，所以子区域能覆盖父容器的落点 —— 这个顺序不能反过来写。
 */
private fun setClicks(
    views: RemoteViews,
    context: Context,
    hasTasks: Boolean,
    hasMoney: Boolean
) {
    when {
        hasTasks && hasMoney -> {
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.WIDGET_TAB_TODAY))
            views.setOnClickPendingIntent(R.id.money_sum, openApp(context, MainActivity.WIDGET_TAB_LEDGER))
            views.setOnClickPendingIntent(R.id.money_label, openApp(context, MainActivity.WIDGET_TAB_LEDGER))
            views.setOnClickPendingIntent(R.id.money_rows, openApp(context, MainActivity.WIDGET_TAB_LEDGER))
        }

        hasTasks ->
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.WIDGET_TAB_TODAY))

        else ->
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.WIDGET_TAB_LEDGER))
    }
}

/**
 * 「今天」后面挂一句还有多少没显示。
 *
 * 不写成「已完成 2/5」：那是进度，而进度条会把清单变成考核 ——
 * 这个 App 从 v0.2 起就明确不做。
 * 「还有 3 件没显示」是一句陈述，不评价你做到哪了。
 */
private fun labelText(context: Context, s: WidgetSnapshot): String {
    val base = context.getString(R.string.widget_today)
    return if (s.hiddenTasks > 0) {
        base + " · " + context.getString(R.string.widget_more, s.hiddenTasks)
    } else {
        base
    }
}

private fun taskRow(
    context: Context,
    task: WidgetTask,
    c: WidgetColors,
    owner: Class<*>
): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_task_row)

    row.setTextViewText(
        R.id.row_mark,
        context.getString(if (task.done) R.string.widget_mark_done else R.string.widget_mark_open)
    )
    row.setTextColor(R.id.row_mark, c.muted)
    row.setTextViewText(R.id.row_text, task.content)
    row.setTextColor(R.id.row_text, if (task.done) c.muted else c.text)

    // 点整行就勾掉 / 撤销。给根布局设一次就够：
    // TextView 默认不消费点击，事件会落到父容器上，所以点字和点圈一个效果。
    row.setOnClickPendingIntent(R.id.row_root, toggleIntent(context, owner, task.id))
    return row
}

private fun moneyRow(context: Context, money: WidgetMoney, c: WidgetColors): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_money_row)
    row.setTextViewText(R.id.money_row_note, money.note)
    row.setTextColor(R.id.money_row_note, c.muted)
    row.setTextViewText(R.id.money_row_amount, MoneyUtil.format(money.amountCents))
    row.setTextColor(R.id.money_row_amount, c.muted)
    row.setOnClickPendingIntent(R.id.money_row_root, openApp(context, MainActivity.WIDGET_TAB_LEDGER))
    return row
}

/** 空态那一行。借用账目行的骨架，因为没有第二个文字样式需要维护 */
private fun plainRow(context: Context, text: String, color: Int): RemoteViews {
    val row = RemoteViews(context.packageName, R.layout.widget_money_row)
    row.setTextViewText(R.id.money_row_note, text)
    row.setTextColor(R.id.money_row_note, color)
    row.setTextViewText(R.id.money_row_amount, "")
    return row
}

/**
 * 勾选用的 PendingIntent。
 *
 * **唯一性靠 data 而不是 requestCode** —— requestCode 是 Int，taskId 是 Long，
 * 截断之后两条不同的任务会共用一个 PendingIntent，表现是「点 A 勾了 B」。
 * 用 `tonight://widget/task/<id>` 做 data，每个 id 天然一个，
 * 这才是这类问题的正确解法。
 *
 * [owner] 是**触发它的那张卡的 provider 类**，不是固定的某一个 ——
 * 四张卡各自有独立的组件 id，广播发给其中任何一个都只和那张卡有关，
 * 但四张卡都能勾同一条任务，所以处理动作在基类里统一。
 */
private fun toggleIntent(context: Context, owner: Class<*>, taskId: Long): PendingIntent {
    val intent = Intent(context, owner).apply {
        action = BaseWidgetProvider.ACTION_TOGGLE_TASK
        data = Uri.parse("tonight://widget/task/$taskId")
        putExtra(BaseWidgetProvider.EXTRA_TASK_ID, taskId)
    }
    return PendingIntent.getBroadcast(context, 0, intent, PI_FLAGS)
}

private fun openApp(context: Context, tab: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_TAB, tab)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    // 两个落点用 tab 名做区分，与 toggleIntent 是同一个理由
    return PendingIntent.getActivity(context, tab.hashCode(), intent, PI_FLAGS)
}
