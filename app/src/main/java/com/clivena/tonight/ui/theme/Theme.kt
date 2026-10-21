package com.clivena.tonight.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.clivena.tonight.data.SettingsRepository

/*
 * 这里仍然不调用 isSystemInDarkTheme()。
 *
 * 明暗由用户那个开关决定，不由系统的深色设置决定 ——
 * 系统的深色管整个手机，这个开关只管这四个 Tab，两个口径混在一起，
 * 就会出现「我明明关掉了它却还是黑的」这种找不到原因的事。
 *
 * 历史：
 *   · v0.6 规划屏单独走深档（睡前低刺激）
 *   · v0.6.1 撤掉 —— 四个 Tab 里只有它翻黑，切过去像进了另一个 App
 *   · v0.7 重新有夜间模式，但这次是**用户按的**，四个 Tab 同时变
 */

/**
 * 全 App 唯一的主题入口。
 *
 * [night] 来自用户在顶栏点的那个月亮，不是按 Tab 推算的。
 * 一个开关管全部四屏，切 Tab 时底色不会跳 —— 这是 v0.6.1 定下的原则，
 * 夜间模式不能把它破坏掉。
 *
 * [background] 是用户选的自定义背景图，日夜都铺：
 * 夜里只是把遮罩压得更重，而不是把图拿掉。给夜间单独开一个例外，
 * 等于又一次「某一屏偷偷不一样」。
 *
 * [backgroundDim] 是用户调的遮罩浓度（越大图越淡），来自设置里的滑杆。
 * 这里只负责它在夜间的偏移，不管它的取值范围 —— 那是数据层的事。
 */
@Composable
fun TonightTheme(
    palette: Palette,
    night: Boolean = false,
    background: ImageBitmap? = null,
    backgroundDim: Float = SettingsRepository.DEFAULT_BG_DIM,
    content: @Composable () -> Unit
) {
    val tone = if (night) palette.night else palette.light

    // 夜间在用户选的基础上再压厚一档：「夜里更淡」是 v0.6 定的，
    // 现在依然成立 —— 用户调的是「白天看多清楚」，不是「此刻看多清楚」。
    //
    // 已经调到头（图最淡）时 clamp 住，退化成日夜一样，不报错。
    // 与其在这里再做一次「夜间要不要例外」的判断，不如让它自然饱和。
    val overlayAlpha = if (night) {
        (backgroundDim + NIGHT_EXTRA_DIM).coerceAtMost(SettingsRepository.MAX_BG_DIM)
    } else {
        backgroundDim
    }

    val colorScheme = if (night) {
        darkColorScheme(
            primary = tone.accent,
            onPrimary = tone.background,
            background = tone.background,
            surface = tone.surface,
            onBackground = tone.textPrimary,
            onSurface = tone.textPrimary,
            onSurfaceVariant = tone.textSecondary,
            outline = tone.outline
        )
    } else {
        lightColorScheme(
            primary = tone.accent,
            onPrimary = tone.surface,
            background = tone.background,
            surface = tone.surface,
            onBackground = tone.textPrimary,
            onSurface = tone.textPrimary,
            onSurfaceVariant = tone.textSecondary,
            outline = tone.outline
        )
    }

    MaterialTheme(colorScheme = colorScheme, typography = TonightTypography) {
        // 底色永远由主题层自己铺。各屏的 Surface 改成透明，
        // 否则它们会把背景图整个盖住 —— 这是把背景图做进主题层的必然后果。
        Box(
            Modifier
                .fillMaxSize()
                .background(tone.background)
        ) {
            if (background != null) {
                Image(
                    bitmap = background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 遮罩不是「好看」，是「能用」：任何一张照片的明暗分布都是随机的，
                // 不压一层背景色，正文对比度就没法保证。
                //
                // 浓度由用户在设置里拖那个滑杆定（0.75 最清楚 ~ 0.96 最淡），
                // 夜间再自动加厚 NIGHT_EXTRA_DIM。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(tone.background.copy(alpha = overlayAlpha))
                )
            }
            content()
        }
    }
}

/**
 * 夜间比白天再厚多少。
 *
 * 0.05 是 v0.7 沿用的差值（白天 0.88 / 夜间 0.93），
 * 换成滑杆之后这个差值不变 —— 用户调白天那一档，夜里自动跟着退。
 * 不做「日夜分别保存两个值」：那等于让用户为同一件事理解两遍，
 * 而他真正的意图只有一句「图要清楚到什么程度」。
 */
private const val NIGHT_EXTRA_DIM = 0.05f
