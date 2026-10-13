package com.clivena.tonight.data

import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 记账数据的唯一出口。
 *
 * 和 TaskRepository 分开：两个模块各管各的数据，
 * 将来任何一个要接云同步，都不会牵连另一个。
 */
interface ExpenseRepository {

    /** 某天的全部账目 */
    fun observeDay(date: LocalDate): Flow<List<Expense>>

    /**
     * 三个口径的合计。
     *
     * [anchor] 是「把哪天当作今天」—— 界面上翻看历史日期时，
     * 汇总卡仍要锚定在真正的今天，不能跟着明细一起往回走。
     */
    fun observeTotals(anchor: LocalDate): Flow<LedgerTotals>

    /**
     * 记一笔。
     *
     * 金额必须为正 —— @return false 表示金额非法（<=0）或写入被拒。
     * 允许 [date] 是过去的日期，这是「补记」的基础。
     */
    suspend fun add(amountCents: Long, note: String, date: LocalDate): Boolean

    /** 改动已有的一笔。@return false 表示金额非法 */
    suspend fun edit(expense: Expense, amountCents: Long, note: String): Boolean

    suspend fun remove(id: Long)
}
