package com.clivena.tonight.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.ui.theme.PaletteId
import com.clivena.tonight.ui.theme.Palettes

/**
 * 小组件用的那几个颜色。
 *
 * 刻意**不另存一份色值**，而是直接读 App 那套色板 ——
 * 复制一份意味着以后改色板要记得改两处，而"改了 App 忘了改桌面"
 * 是那种半年后才会被发现、且没人知道去哪修的错误。
 *
 * 用 Compose 的 `Color.toArgb()` 只取一个 int，不涉及任何 Compose 运行时：
 * 小组件仍然走 RemoteViews，不因为这一行就把 Compose 拉进渲染路径。
 */
data class WidgetColors(
    /**
     * 是不是夜间档。
     *
     * 卡片底色只靠它二选一（两张 drawable），不参与色板 ——
     * 五套色板的底色肉眼几乎分不出来，为它做十张图是给自己挖坑。
     * 色板体现在 [accent] 上，那个是真的能看出来的。
     */
    val night: Boolean,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val divider: Int
)

/**
 * 跟随用户选的两件事：色板和 ☾ 开关。
 *
 * 注意这里跟的是 **App 内的开关**，不是系统深色。
 * 桌面组件通常只能看系统设置，但我们要的是「App 是什么样，桌面就是什么样」——
 * 用两个口径会出现「App 是浅色、桌面是深色」，比不做还糟。
 */
fun readWidgetColors(context: Context): WidgetColors {
    val settings = SettingsRepository(context)
    val palette = Palettes.of(PaletteId.fromName(settings.paletteName.value))
    val night = settings.nightMode.value
    val tone = if (night) palette.night else palette.light
    return WidgetColors(
        night = night,
        text = tone.textPrimary.toArgb(),
        muted = tone.textSecondary.toArgb(),
        accent = tone.accent.toArgb(),
        divider = tone.outline.toArgb()
    )
}
