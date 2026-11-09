package com.clivena.tonight.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 所有小组件实例的统一刷新入口。
 *
 * 桌面上可能同时存在四种卡片（4×2 / 5×2 / 3×2 / 2×2），它们是四个独立的
 * `AppWidgetProvider`，各自有自己的 ComponentName 和一个 id 列表。
 * 因此「刷新」这件事只有两个入口：
 *
 *  - [refreshAllNow]：**所有**卡片一起刷 —— 数据变了、切了明暗、勾了一条任务，
 *    凡是会影响多张卡的操作都走这个。
 *  - [refreshProviderNow]：只刷一种卡片 —— 收到系统派给某个 provider 的
 *    UPDATE / OPTIONS_CHANGED 时走这个。
 *
 * 为什么不写「一个 provider 只刷自己」就算完：同一条任务会同时出现在
 * 3×2、4×2、5×2 三张卡上。在 3×2 上勾掉它，另外两张不跟着变，
 * 用户看到的就是「我明明勾了，另一块还是没勾」—— 而这种不一致
 * 比完全不刷新更让人怀疑数据到底存没存上。
 */
object WidgetRefresher {

    /**
     * 桌面上可能出现的全部卡片类型。
     *
     * **新增卡片时必须往这里加一行**，漏掉的后果是「那张卡永远不会自己更新」，
     * 而且只会表现为「有时对有时不对」，极难联想到这个列表。
     * `verify_widget.py` 会核对这个列表与 AndroidManifest 里的 receiver 一一对应。
     */
    val PROVIDERS: List<Class<out BaseWidgetProvider>> = listOf(
        TonightWidgetProvider::class.java,  // 4×2 合并（v0.11 原有）
        WideWidgetProvider::class.java,     // 5×2 合并
        TodayWidgetProvider::class.java,    // 3×2 只有待办
        LedgerWidgetProvider::class.java,   // 2×2 只有记账
    )

    /** 从任意线程触发一次全量刷新。桌面上一个组件都没有时直接返回，连数据库都不开 */
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        if (!anyOnScreen(appContext)) return
        CoroutineScope(Dispatchers.IO).launch { refreshAllNow(appContext) }
    }

    suspend fun refreshAllNow(context: Context) {
        PROVIDERS.forEach { refreshProviderNow(context, it) }
    }

    /**
     * 只刷某一种卡片。
     *
     * provider 是无状态的，靠反射构造一个实例来拿它的布局与行数配置 ——
     * 比在刷新链路里维护一张「类名 → 配置」的映射表更不容易漏：
     * 那张表迟早会和类本身对不上。
     */
    suspend fun refreshProviderNow(context: Context, clazz: Class<out BaseWidgetProvider>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, clazz))
        if (ids.isEmpty()) return

        val provider = clazz.getDeclaredConstructor().newInstance()
        ids.forEach { id -> renderWidget(context, manager, id, provider) }
    }

    private fun anyOnScreen(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return PROVIDERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }
}
