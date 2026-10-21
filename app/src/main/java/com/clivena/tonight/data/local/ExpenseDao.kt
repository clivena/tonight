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

/**
 * 一条常用备注的统计结果。
 *
 * 两个排序键缺一不可：
 * - 光按使用次数排，「上周才开始记的午饭」永远挤不过半年前的老标签；
 * - 光按最近使用排，一次性的临时备注会把真正常用的挤下去。
 *
 * 先次数、后时间 —— 常见优先，同频的取新的。
 */
data class NoteStatRow(
    val note: String,
    val useCount: Int,
    val lastUsedAt: Long
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

    /**
     * 常用备注，按「用得最多、其次最近」取前 [limit] 个。
     *
     * 这条查询是「记一笔」提速的全部秘密：记账的真实分布是少数名目
     * 反复出现（午饭、通勤、咖啡），把高频的那几个抬到一键，
     * 比优化输入法有用得多。
     *
     * 关键：它**没有新增任何表或列**。备注本来就是自由文本，
     * GROUP BY 一下就是标签体系 —— 用户不用维护分类，
     * 也就不存在「打标签的麻烦让人放弃记账」这件事。
     *
     * IFNULL 是为了让 MAX 结果在 Kotlin 侧稳定是非空 Long：
     * 有 GROUP BY 时不可能整组 NULL，但 SQLite 的类型推断仍然允许可空，
     * 若不裹一层，Room 会把这一列当成 Long? 而拒绝映射。
     */
    @Query(
        "SELECT note, COUNT(*) AS useCount, IFNULL(MAX(createdAt), 0) AS lastUsedAt " +
            "FROM expenses WHERE note != '' " +
            "GROUP BY note ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit"
    )
    fun observeFrequentNotes(limit: Int): Flow<List<NoteStatRow>>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("UPDATE expenses SET amountCents = :amountCents, note = :note, date = :date, updatedAt = :now WHERE id = :id")
    suspend fun update(id: Long, amountCents: Long, note: String, date: String, now: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
