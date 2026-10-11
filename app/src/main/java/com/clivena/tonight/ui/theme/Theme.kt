package com.clivena.tonight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/*
 * 这里刻意不调用 isSystemInDarkTheme()。
 *
 * 深浅色在这个产品里是「功能」，不是「偏好」：
 *   · 今晚屏必须深色 —— 睡前低刺激
 *   · 明早屏必须浅色 —— 醒来一眼看清
 *
 * 如果跟随系统，一个开着浅色模式的用户晚上会看到刺眼的白色界面，
 * 产品意图当场破功。所以两套主题硬绑定到各自的屏幕上。
 */

private val NightColors = darkColorScheme(
    primary = NightAccent,
    background = NightBg,
    surface = NightSurface,
    onPrimary = NightBg,
    onBackground = NightTextPrimary,
    onSurface = NightTextPrimary,
    onSurfaceVariant = NightTextSecondary,
    outline = NightOutline
)

private val DayColors = lightColorScheme(
    primary = DayAccent,
    background = DayBg,
    surface = DaySurface,
    onPrimary = DaySurface,
    onBackground = DayTextPrimary,
    onSurface = DayTextPrimary,
    onSurfaceVariant = DayTextSecondary,
    outline = DayOutline
)

/** 今晚屏：始终深色 */
@Composable
fun TonightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        typography = TonightTypography,
        content = content
    )
}

/** 明早屏：始终浅色 */
@Composable
fun TomorrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DayColors,
        typography = TonightTypography,
        content = content
    )
}
