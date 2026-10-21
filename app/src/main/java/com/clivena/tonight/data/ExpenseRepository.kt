package com.clivena.tonight.data

import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
import com.clivena.tonight.domain.MonthLedger
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

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
     * 某个整月的按日合计，给月历用。
     *
     * 和 [observeTotals] 是两种东西：那个回答「此刻花了多少」，
     * 这个回答「那个月每天各花了多少」。
     */
    fun observeMonth(month: YearMonth): Flow<MonthLedger>

    /**
     * 常用备注，给「记一笔」做一键填入。
     *
     * 它是从账目本身算出来的，不是用户维护的标签体系 ——
     * 记的账越多，标签越贴合自己的习惯，而用户一次都不用整理。
     * 没有记录时返回空列表，由界面决定要不要给兜底。
     */
    fun observeNoteChips(limit: Int = 8): Flow<List<String>>

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
