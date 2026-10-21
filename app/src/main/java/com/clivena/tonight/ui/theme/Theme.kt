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

/*
 * 这里仍然不调用 isSystemInDarkTheme()。
 *
 * 明暗在这个产品里是「功能」，不是「偏好」：
 *   · 规划屏走深档 —— 睡前低刺激
 *   · 其余走浅档 —— 白天一眼看清
 *
 * v0.6 改的是「色系」而不是「明暗」：
 * 以前两档各自写死（浅档蓝灰、深档无彩深灰），切 Tab 时是从一个色系跳到另一个；
 * 现在两档出自同一套 [Palette]，切 Tab 看到的是同一个颜色的明暗变化。
 */

/**
 * 全 App 唯一的主题入口。
 *
 * [night] 由调用方按当前屏决定 —— 只有「规划」是深档。
 * [background] 是用户选的自定义背景图，只在浅档生效：
 * 睡前那一面不该有任何画面刺激。
 */
@Composable
fun TonightTheme(
    palette: Palette,
    night: Boolean,
    background: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val tone = if (night) palette.night else palette.light

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
            if (!night && background != null) {
                Image(
                    bitmap = background,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // 遮罩不是「好看」，是「能用」：任何一张照片的明暗分布都是随机的，
                // 不压一层背景色，正文对比度就没法保证。0.88 是「图还看得见、
                // 字一定读得清」的那个位置。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(tone.background.copy(alpha = 0.88f))
                )
            }
            content()
        }
    }
}
