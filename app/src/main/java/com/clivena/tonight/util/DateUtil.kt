package com.clivena.tonight.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtil {

    /**
     * 几点之后打开「规划」屏，默认指向的是明天。
     *
     * v0.2 起这个数字**没有任何强制力**了 —— 界面上「今天 / 明天」随时可以手动切换。
     * 它只决定你第一眼看到的是哪一天，仅此而已。
     */
    private const val EVENING_HOUR = 18

    private val FULL_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

    /** 「规划」屏打开时默认指向哪一天：18 点后 → 明天，否则 → 今天 */
    fun defaultPlanDate(now: LocalDateTime = LocalDateTime.now()): LocalDate =
        if (now.hour >= EVENING_HOUR) now.toLocalDate().plusDays(1) else now.toLocalDate()

    fun today(): LocalDate = LocalDate.now()

    /** 标题用：今天 / 明天 / 昨天 / 10月18日 */
    fun labelFor(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.plusDays(1) -> "明天"
        else -> date.format(FULL_DATE)
    }

    /** 副标题用：10月12日 星期一 */
    fun fullLabel(date: LocalDate): String = date.format(FULL_DATE)
}
