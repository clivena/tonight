package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 按月聚合出来的一行：某一天花了多少。
 *
 * 只有**有花销的那些天**会出现在结果里 —— 没花销的天不必占位，
 * 月历那边查不到就是「这天没花钱」，语义和缺省值天然一致。
 */
data class DailyTotalRow(
    val date: String,
    val amountCents: Long
)

@Dao
interface ExpenseDao {

    /** 某天的账，新记的在前面（同一天里按登记时间倒序） */
    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY createdAt DESC, id DESC")
    fun observeByDate(date: String): Flow<List<ExpenseEntity>>

    /**
     * 一个日期区间的合计。
     *
     * 两个细节：
     * 1. date 存的是 yyyy-MM-dd，**字典序就等于时间序**，所以 BETWEEN 直接比字符串即可，
     *    不需要把日期存成时间戳。
     * 2. 返回 Long? 而不是 COALESCE(...,0)：区间内没有记录时 SUM 返回 NULL，
     *    在这里返回可空、由仓储层兜底成 0，比在 SQL 里糊一层 COALESCE 更好排查。
     */
    @Query("SELECT SUM(amountCents) FROM expenses WHERE date BETWEEN :from AND :to")
    fun observeSum(from: String, to: String): Flow<Long?>

    /**
     * 一个区间里**每天**的合计，用于月历。
     *
     * 用 GROUP BY 让 SQLite 干分组，而不是把整月明细查回来在内存里 groupBy ——
     * 记账是「长期只增不减」的数据，一年就是几千行，
     * 月历只需要 31 个数字，没必要为它把明细全捞上来。
     *
     * 因为带 GROUP BY，没有记录时是「没有行」，而不是「一行 NULL」，
     * 所以这里的 amountCents 可以是非空的 Long。
     */
    @Query(
        "SELECT date, SUM(amountCents) AS amountCents FROM expenses " +
            "WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"
    )
    fun observeDailyTotals(from: String, to: String): Flow<List<DailyTotalRow>>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("UPDATE expenses SET amountCents = :amountCents, note = :note, date = :date, updatedAt = :now WHERE id = :id")
    suspend fun update(id: Long, amountCents: Long, note: String, date: String, now: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
