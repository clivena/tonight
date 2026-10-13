package com.clivena.tonight.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 一个闭区间 —— 两端都含 */
data class DateRange(val first: LocalDate, val last: LocalDate)

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

    /**
     * 这一周，**周一为一周之始**。
     *
     * 不用「周日开始」：发工资、还信用卡、看账单的周期都是周一，
     * 「本周花了多少」必须和用户脑子里的那个「本周」是同一个区间，
     * 否则数字对上了，直觉对不上。
     */
    fun weekRange(date: LocalDate): DateRange {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DateRange(monday, monday.plusDays(6))
    }

    /** 这一月，1 号到月末 */
    fun monthRange(date: LocalDate): DateRange =
        DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
}
