package com.clivena.tonight.ui.theme

import androidx.compose.ui.graphics.Color

// ── 今晚屏（始终深色）─────────────────────────────────────
// 用近黑而不是纯黑：纯黑在 OLED 上和内容的对比过强，睡前盯着累。
val NightBg = Color(0xFF121316)
val NightSurface = Color(0xFF1C1E22)
val NightTextPrimary = Color(0xFFE8E6E3)
val NightTextSecondary = Color(0xFF83868C)
val NightAccent = Color(0xFF8AA6CB)
val NightOutline = Color(0xFF2A2D33)

// ── 明早屏（始终浅色）─────────────────────────────────────
// 早上要一眼看清，所以对比度给足。
val DayBg = Color(0xFFFAFAF8)
val DaySurface = Color(0xFFFFFFFF)
val DayTextPrimary = Color(0xFF1A1A1A)
val DayTextSecondary = Color(0xFF787878)
val DayAccent = Color(0xFF2F5D8C)
val DayOutline = Color(0xFFE4E4E0)
