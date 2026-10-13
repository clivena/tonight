package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("UPDATE expenses SET amountCents = :amountCents, note = :note, date = :date, updatedAt = :now WHERE id = :id")
    suspend fun update(id: Long, amountCents: Long, note: String, date: String, now: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
