package com.clivena.tonight.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * 一个月的账，按天聚好。
 *
 * 只存「哪天花了多少」这一张表，其余（合计、有花销的天数、单日峰值）
 * 全部由它**当场算出来**，不另存快照。
 *
 * 理由和 LedgerTotals 一样：账本最怕的就是缓存和对账不一致。
 * 31 个数字的求和是免费的，但多存一份就等于多一个可能说谎的地方。
 */
data class MonthLedger(
    val month: YearMonth,
    /** 键是这天，值是当天合计（分）。没有花销的天不在表里 */
    val dailyTotals: Map<LocalDate, Long> = emptyMap()
) {

    /** 整月合计 */
    val totalCents: Long get() = dailyTotals.values.sum()

    /** 这个月有几天在花钱 —— 比「平均每天」诚实：没花钱的天不是 0，是「没记」 */
    val activeDays: Int get() = dailyTotals.size

    /**
     * 这个月花得最多的一天。
     *
     * 月历的深浅就是拿每一天去除它得到的比值 ——
     * 也就是说，颜色回答的是「这天相对这个月算多算少」，
     * 而不是「这天绝对花了多少」。后者跨月看没有意义：
     * 一顿 100 块的饭，在月消费 300 的月份里是大头，在月消费 8000 的月份里不算什么。
     */
    val peakDayCents: Long get() = dailyTotals.values.maxOrNull() ?: 0L

    fun amountOn(date: LocalDate): Long? = dailyTotals[date]
}
