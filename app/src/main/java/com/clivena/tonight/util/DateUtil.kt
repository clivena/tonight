package com.clivena.tonight.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
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
     * 明细导航用：今天 · 10/21、昨天 · 10/20、10/18。
     *
     * 不用 [labelFor] 的「10月18日 星期二」—— 那一行左右各有一个箭头，
     * 全称会把箭头挤到屏幕边缘。况且记账时「星期几」没有决策价值，
     * 用户只关心是哪一天。
     */
    fun navLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val monthDay = "${date.monthValue}/${date.dayOfMonth}"
        return when (date) {
            today -> "今天 · $monthDay"
            today.minusDays(1) -> "昨天 · $monthDay"
            else -> monthDay
        }
    }

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

    /** 整月，1 号到月末 */
    fun monthRange(month: YearMonth): DateRange = monthRange(month.atDay(1))

    /** 月份导航用：2026年10月。不补零 —— 「10月」比「10月」更像人话里说的月份 */
    fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    /**
     * 月历要铺的格子，长度固定是 7 的整数倍。
     *
     * null 是占位（不属于这个月的格），前面补几个取决于 1 号是星期几。
     * 第一列是**周一**，和 weekRange 保持同一套周界，否则「本周」和月历上的行对不上。
     *
     * 返回长度是 35 或 42 —— 5 行或 6 行，不会出现 4 行的月亮月，
     * 所以月历的高度是稳定的，翻月时下面那一块不会跳。
     */
    fun monthGridDays(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = ArrayList<LocalDate?>(42)
        repeat(lead) { cells.add(null) }
        for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }
}
