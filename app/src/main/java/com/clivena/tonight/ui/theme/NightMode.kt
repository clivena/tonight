package com.clivena.tonight.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 夜间模式的两个值，往下传而不用逐层透传。
 *
 * ##### 为什么用 CompositionLocal，而不是给每个 Screen 加参数
 *
 * 顶栏要在**五个屏**里都读到它，而那几个 Screen 自己根本不用 ——
 * 这种「只为往下递而存在」的参数，每加一个跨屏功能就要多穿一层。
 * 夜间模式又恰好是个纯全局状态，正是 CompositionLocal 该管的事。
 *
 * 注意它和 v0.6 那个 `night` 不是一回事：那时候的 night 是**按 Tab 推算**的，
 * 这里是**用户按的**。前者会随 Tab 变，后者不会。
 *
 * ##### 为什么拆成两个
 *
 * - [LocalNightMode] 用 `compositionLocalOf`：值变化时，只有真正读它的
 *   那几个地方重组（顶栏那个月亮），不必波及整棵树。
 * - [LocalToggleNightMode] 用 `staticCompositionLocalOf`：里面装的是
 *   一个稳定的函数引用，不会随状态变化，用 static 版本更省。
 */
val LocalNightMode = compositionLocalOf { false }

val LocalToggleNightMode = staticCompositionLocalOf<() -> Unit> { {} }
