package com.clivena.tonight.data

import com.clivena.tonight.data.local.ExpenseDao
import com.clivena.tonight.data.local.ExpenseEntity
import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
import com.clivena.tonight.domain.MonthLedger
import com.clivena.tonight.util.DateUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class LocalExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {

    override fun observeDay(date: LocalDate): Flow<List<Expense>> =
        dao.observeByDate(date.toString()).map { list -> list.map { it.toDomain() } }

    override fun observeTotals(anchor: LocalDate): Flow<LedgerTotals> {
        val day = anchor.toString()
        val week = DateUtil.weekRange(anchor)
        val month = DateUtil.monthRange(anchor)

        // 三条独立的聚合查询，任何一张表有写入都会各自重新发射 —— 三个数字永远同步。
        return combine(
            dao.observeSum(day, day),
            dao.observeSum(week.first.toString(), week.last.toString()),
            dao.observeSum(month.first.toString(), month.last.toString())
        ) { d, w, m ->
            LedgerTotals(todayCents = d ?: 0L, weekCents = w ?: 0L, monthCents = m ?: 0L)
        }
    }

    override fun observeMonth(month: YearMonth): Flow<MonthLedger> {
        val range = DateUtil.monthRange(month)
        return dao.observeDailyTotals(range.first.toString(), range.last.toString())
            .map { rows ->
                MonthLedger(
                    month = month,
                    dailyTotals = rows.associate { LocalDate.parse(it.date) to it.amountCents }
                )
            }
    }

    override fun observeNoteChips(limit: Int): Flow<List<String>> =
        dao.observeFrequentNotes(limit)
            .map { rows -> rows.map { it.note } }
            // 标签只是提速的辅助，查询出问题不该让整屏记账挂掉 —— 退化成空列表，
            // 界面自然回到「自己写备注」那条老路。
            .catch { emit(emptyList()) }

    override suspend fun add(amountCents: Long, note: String, date: LocalDate): Boolean {
        if (amountCents <= 0L) return false
        val now = System.currentTimeMillis()
        dao.insert(
            ExpenseEntity(
                date = date.toString(),
                amountCents = amountCents,
                note = note.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    override suspend fun edit(expense: Expense, amountCents: Long, note: String): Boolean {
        if (amountCents <= 0L) return false
        dao.update(
            id = expense.id,
            amountCents = amountCents,
            note = note.trim(),
            // 编辑不改归属日期 —— 改期是把账搬到另一天，属于另一个动作，
            // 这一版不做，免得「哪天的账」被无声无息改掉。
            date = expense.date.toString(),
            now = System.currentTimeMillis()
        )
        return true
    }

    override suspend fun remove(id: Long) {
        // 真删而不是打软删除标记：账本记错了就是记错了，
        // 留一堆看不见的墓碑，只会让「本月合计」和明细对不上时更难查。
        dao.delete(id)
    }

    private fun ExpenseEntity.toDomain() = Expense(
        id = id,
        date = LocalDate.parse(date),
        amountCents = amountCents,
        note = note
    )
}
