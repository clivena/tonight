package com.clivena.tonight.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 一套色板的某一档 —— 浅色档或深色档。
 *
 * 只有六个语义变量，不再多：背景、表面、主色、正文、次要文字、分隔线。
 * 变量一多，配色就必然有地方配不齐，最后变成「这个屏看着还行，那个屏有点脏」。
 */
data class Tone(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color
)

/**
 * 一套主题 = 同一个色系的浅档 + 深档。
 *
 * 这是 v0.6 解决「颜色不统一」的方式：
 * 以前是「浅色档用蓝灰、深色档用无彩深灰」—— 切 Tab 时不是变明暗，是换了个色系。
 * 现在两档出自同一套色板，切 Tab 看到的是同一种颜色的明暗变化。
 */
data class Palette(
    val id: PaletteId,
    val light: Tone,
    val night: Tone
)

/**
 * 五套预设。
 *
 * 不做「自选任意主色」：好看在于配色是调出来的，不是算出来的 ——
 * 开放调色盘，派生色会有一半达不到对比度。这五套的主色都是手工压过亮度的，
 * 保证白字压在主色上对比度在 5:1 以上、深字压在浅背景上在 12:1 以上。
 */
enum class PaletteId(val label: String) {
    INDIGO("靛蓝"),
    MOSS("墨绿"),
    OCHRE("赭石"),
    LOTUS("藕粉"),
    STONE("岩灰");

    companion object {
        /** 存进 SharedPreferences 的是枚举名；读不出来（被改坏、老版本）就回默认 */
        fun fromName(name: String?): PaletteId =
            entries.firstOrNull { it.name == name } ?: INDIGO
    }
}

object Palettes {

    val ALL: List<Palette> = listOf(
        // 靛蓝 —— 默认。冷，像旧账本的蓝黑墨水
        Palette(
            id = PaletteId.INDIGO,
            light = Tone(
                background = Color(0xFFFAFAF8),
                surface = Color(0xFFFFFFFF),
                accent = Color(0xFF2F5D8C),
                textPrimary = Color(0xFF1A1A1A),
                textSecondary = Color(0xFF787878),
                outline = Color(0xFFE4E4E0)
            ),
            night = Tone(
                background = Color(0xFF111318),
                surface = Color(0xFF1C1E22),
                accent = Color(0xFF8AA6CB),
                textPrimary = Color(0xFFE8E6E3),
                textSecondary = Color(0xFF83868C),
                outline = Color(0xFF2A2D33)
            )
        ),

        // 墨绿 —— 稳，像台灯下的绿罩子
        Palette(
            id = PaletteId.MOSS,
            light = Tone(
                background = Color(0xFFF9FBF8),
                surface = Color(0xFFFFFFFF),
                accent = Color(0xFF2E6B4F),
                textPrimary = Color(0xFF1A1D1A),
                textSecondary = Color(0xFF787F7A),
                outline = Color(0xFFE2E8E2)
            ),
            night = Tone(
                background = Color(0xFF101413),
                surface = Color(0xFF1A1F1D),
                accent = Color(0xFF8FBFA6),
                textPrimary = Color(0xFFE6E9E6),
                textSecondary = Color(0xFF828A85),
                outline = Color(0xFF29302D)
            )
        ),

        // 赭石 —— 暖，最接近纸和木头
        Palette(
            id = PaletteId.OCHRE,
            light = Tone(
                background = Color(0xFFFCFAF6),
                surface = Color(0xFFFFFFFF),
                accent = Color(0xFFA35A32),
                textPrimary = Color(0xFF1D1A17),
                textSecondary = Color(0xFF7F7873),
                outline = Color(0xFFEAE3DB)
            ),
            night = Tone(
                background = Color(0xFF151110),
                surface = Color(0xFF201B18),
                accent = Color(0xFFD9A483),
                textPrimary = Color(0xFFE9E4DF),
                textSecondary = Color(0xFF8A827C),
                outline = Color(0xFF2E2723)
            )
        ),

        // 藕粉 —— 柔，但主色压得够深，不会变成糖水
        Palette(
            id = PaletteId.LOTUS,
            light = Tone(
                background = Color(0xFFFCFAFB),
                surface = Color(0xFFFFFFFF),
                accent = Color(0xFF9D5669),
                textPrimary = Color(0xFF1D1A1B),
                textSecondary = Color(0xFF7E7479),
                outline = Color(0xFFEAE2E5)
            ),
            night = Tone(
                background = Color(0xFF141113),
                surface = Color(0xFF1F1A1D),
                accent = Color(0xFFD3A0AF),
                textPrimary = Color(0xFFE9E3E6),
                textSecondary = Color(0xFF8A8186),
                outline = Color(0xFF2D2629)
            )
        ),

        // 岩灰 —— 近乎无色相，最安静的一档
        Palette(
            id = PaletteId.STONE,
            light = Tone(
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFFFFFFF),
                accent = Color(0xFF4A4E52),
                textPrimary = Color(0xFF1A1A1A),
                textSecondary = Color(0xFF787878),
                outline = Color(0xFFE4E4E4)
            ),
            night = Tone(
                background = Color(0xFF121314),
                surface = Color(0xFF1C1E20),
                accent = Color(0xFFA8ADB2),
                textPrimary = Color(0xFFE8E6E3),
                textSecondary = Color(0xFF83868C),
                outline = Color(0xFF2A2D30)
            )
        )
    )

    fun of(id: PaletteId): Palette = ALL.first { it.id == id }
}
