package com.clivena.tonight.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

/*
 * 这里不调用 isSystemInDarkTheme()。
 *
 * 明暗不是「偏好」，2026-10 之后也不再是「功能」：
 * 全 App 一个背景色，四个 Tab 共用同一档。色系仍然由用户在设置里选。
 *
 * 之前「规划」单独走深档，理由是睡前低刺激 —— 但四个 Tab 里三个是浅的，
 * 每次切到规划屏整屏翻黑，反而比配色不统一更打眼。
 * 一个「统一」的界面里不该有一个例外，哪怕那个例外有道理。
 *
 * Tone 里的 night 那一档没有删：想恢复某屏的深色，给它传 night=true 就行，
 * 数据层不用动。
 */

/**
 * 全 App 唯一的主题入口。
 *
 * [background] 是用户选的自定义背景图，四个 Tab 都铺 ——
 * 既然是「统一」，就不能有一屏偷偷用纯色。
 */
@Composable
fun TonightTheme(
    palette: Palette,
    background: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val tone = palette.light

    val colorScheme = lightColorScheme(
        primary = tone.accent,
        onPrimary = tone.surface,
        background = tone.background,
        surface = tone.surface,
        onBackground = tone.textPrimary,
        onSurface = tone.textPrimary,
        onSurfaceVariant = tone.textSecondary,
        outline = tone.outline
    )

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
