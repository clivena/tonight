package com.clivena.tonight.domain

import java.time.LocalDate

/**
 * 一笔账（领域模型）。
 *
 * 金额在领域层也是「分」——只有到显示那一刻才换算成元。
 * 让「钱是整数」这条规则一路贯穿到界面，中间任何一环都没有机会引入浮点误差。
 */
data class Expense(
    val id: Long,
    val date: LocalDate,
    val amountCents: Long,
    val note: String
)

/**
 * 三个口径的合计，单位都是「分」。
 *
 * 不存「今日 / 本周 / 本月」三张快照，每次都由 SQL 现算 ——
 * 账本最怕的就是缓存和对账不一致。
 */
data class LedgerTotals(
    val todayCents: Long = 0L,
    val weekCents: Long = 0L,
    val monthCents: Long = 0L
)
