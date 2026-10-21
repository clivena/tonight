package com.clivena.tonight.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
 * 它可以到 0 —— 那时屏幕上没有任何色层，就是那张图本身。
 * 但遮罩归零不意味着文字可以裸着，所以这里还负责按遮罩的薄厚
 * 给内容补一块面板：遮罩让开多少，面板就补多少。
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
    // **但纯图档（0）在夜间也不加这一档。** 加了就永远是 0.05，屏幕上也永远
    // 有一层膜 —— 那等于「完全是背景图」这个选项在夜间不存在，
    // 而用户要的恰恰是它存在。夜里觉得亮就自己往回拖一格，
    // 这件事的判断者从头到尾只有一个。
    //
    // 这一档是**缓入**的，不是一个开关。写成 `if (night && dim > 0)` 会在 0 那一格
    // 突然多出 0.05，拖到头时画面明显一暗 —— 而人眼对「最后一格突变」极其敏感，
    // 那正是这一版在修的那种毛病。所以让它在离开纯图档的一小段里长满。
    val nightExtra = NIGHT_EXTRA_DIM * (backgroundDim / NIGHT_RAMP).coerceIn(0f, 1f)
    val overlayAlpha = if (night) {
        (backgroundDim + nightExtra).coerceAtMost(SettingsRepository.MAX_BG_DIM)
    } else {
        backgroundDim
    }

    // 遮罩越薄，正文越需要别的东西替它撑住可读性 —— 这个东西就是内容面板。
    //
    // 它不是「再加一层膜」，因为**它不铺满屏幕**：四边留出 PANEL_INSET，
    // 图从边缘一直露到面板的圆角边上。视觉上读作「图片是桌面，内容是一张纸」，
    // 而不是「整屏蒙了一层色」。
    //
    // 遮罩自己还够厚（≥ PANEL_FADE_FROM）时面板完全不出现 ——
    // 那种浓度下正文本来就没问题，多一块纸只会白丢一片图。
    // 中间地带按比例过渡，所以拖滑杆时它是渐显的，不会突然跳出来。
    val panelAlpha = if (background == null) {
        0f
    } else {
        val base = if (night) PANEL_ALPHA_NIGHT else PANEL_ALPHA_DAY
        (base * (1f - overlayAlpha / PANEL_FADE_FROM)).coerceIn(0f, base)
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
                // 浓度由用户在设置里拖那个滑杆定（0 纯图 ~ 0.96 最淡），
                // 夜间再自动加厚 NIGHT_EXTRA_DIM（拖到 0 时除外）。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(tone.background.copy(alpha = overlayAlpha))
                )
            }

            // 内容面板让出去的那圈缝，会露出**一点都没压过**的图。
            //
            // 状态栏和导航栏的图标正好压在这圈缝上，而它们的黑白只由那一个月亮
            // 开关决定 —— 它不可能知道底下这张照片是亮是暗。夜里配一张亮图，
            // 白色图标会整个消失。
            //
            // 所以上下各压一条很快消失的渐变。它窄、且浓度和面板一致，
            // 视觉上几乎不构成「滤镜」，但足以让那排图标有依托。
            if (panelAlpha > 0.01f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(EDGE_FADE_HEIGHT)
                        .background(
                            Brush.verticalGradient(
                                0f to tone.background.copy(alpha = panelAlpha),
                                1f to Color.Transparent
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(EDGE_FADE_HEIGHT)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to tone.background.copy(alpha = panelAlpha)
                            )
                        )
                )
            }

            // 内容面板：遮罩让开多少，它就替上多少。
            //
            // 用 surface 而不是 background 做它的色 —— surface 本来就是「卡片色」，
            // 浅色档下比背景更白，压在图上才像一张纸而不是一层灰。
            if (panelAlpha > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(PANEL_INSET)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(PANEL_CORNER))
                            .background(tone.surface.copy(alpha = panelAlpha))
                    ) {
                        content()
                    }
                }
            } else {
                content()
            }
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

/**
 * 夜间那档额外厚度，用多长一段滑程长满。
 *
 * 0.10 意味着：滑杆离开纯图档不到 10% 的地方，夜里还几乎是纯图；
 * 过了这 10% 就回到原来「夜里再厚 0.05」的行为。
 * 取这么短，是因为它只服务于「消掉最后一格的台阶」，
 * 不该让用户在中间地带也感觉到和以前不一样。
 */
private const val NIGHT_RAMP = 0.10f

/**
 * 遮罩厚到这个程度，内容面板就完全不需要了 —— 正文本来就在稳的地面上。
 *
 * 取 0.75 是照 v0.8 那个下限定的：v0.8 认定「0.75 是可以不给任何依托的最薄一档」，
 * 那反过来，0.75 之上就是不需要依托的区间。这个数不是新拍的，是沿用旧结论。
 */
private const val PANEL_FADE_FROM = 0.75f

/**
 * 纯图档（遮罩为 0）下面板的浓度。
 *
 * 白天 0.52：哪怕照片是纯黑，面板下的底色亮度也在 120 以上，
 * 深色正文（#1A1A1A）对比度仍约 5:1 —— 过线但不过分。
 * 夜间 0.66 更厚，因为夜间是浅字压深底，浅字在亮图上掉得比深字在暗图上快得多。
 */
private const val PANEL_ALPHA_DAY = 0.52f
private const val PANEL_ALPHA_NIGHT = 0.66f

/** 面板离屏幕边缘的距离。图就是靠这圈缝被看见的，太窄等于没有 */
private val PANEL_INSET = 10.dp

private val PANEL_CORNER = 22.dp

/**
 * 上下两条边缘渐变的高度。
 *
 * 要盖住状态栏（含挖孔机型，最高约 48dp）和手势条，
 * 所以取 64 而不是刚好贴着图标 —— 渐变挨得太紧，看起来就是一条明显的暗带。
 */
private val EDGE_FADE_HEIGHT = 64.dp
