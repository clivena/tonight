package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 「这一行算不算一篇日记」。
 *
 * 抽成常量而不是在两处各写一遍 SQL 片段，理由很具体：
 * 时间线（往回翻）和日历（哪几天有点）是**两个入口看同一件事**。
 * 一旦两处的过滤条件漂移，就会出现「日历上有点、点进去是空白页」——
 * 它不崩、不报错，只是让人以为日记丢了。用同一个常量表达，
 * 这种不一致在语法层就不可能出现，不需要靠人去记得对齐。
 */
private const val WRITTEN = "content != '' OR imageName IS NOT NULL"

@Dao
interface JournalDao {

    /** 某一天的日记。没有就是 null —— 和「有日记但内容为空」是两件事，不合并 */
    @Query("SELECT * FROM journal WHERE date = :date LIMIT 1")
    fun observe(date: String): Flow<JournalEntity?>

    /**
     * 有内容的那些天，新的在前。给「往回翻」用。
     *
     * 过滤条件是「有字或有图」而不是「有这一行」：
     * 空行不该占时间线的位置，否则翻过去全是空白页。
     */
    @Query(
        "SELECT * FROM journal WHERE $WRITTEN " +
            "ORDER BY date DESC"
    )
    fun observeTimeline(): Flow<List<JournalEntity>>

    /**
     * 写过的那些天，**只取日期列**。给日历用，一眼看出哪几天有内容。
     *
     * 不复用 [observeTimeline] 再在内存里 map 成日期集合：那样每翻一次屏
     * 都要把几百篇正文全读进内存。这个查询只回一列短字符串，代价小到可以忽略。
     * 过滤条件必须和 [observeTimeline] 完全一致 —— 两处共用 [WRITTEN]。
     */
    @Query("SELECT date FROM journal WHERE $WRITTEN ORDER BY date DESC")
    fun observeWrittenDates(): Flow<List<String>>

    /** 首次创建时间。用来在反复保存时保住「这篇是哪天开始写的」 */
    @Query("SELECT createdAt FROM journal WHERE date = :date")
    suspend fun createdAtOf(date: String): Long?

    /**
     * 全部日记，给备份用。
     *
     * 不过滤「有字或有图」—— [observeTimeline] 那个条件的目的是不让空行占时间线，
     * 而备份要的是完整镜像。一条空行也是用户那天的记录。
     */
    @Query("SELECT * FROM journal ORDER BY date ASC")
    suspend fun getAll(): List<JournalEntity>

    @Upsert
    suspend fun upsert(entity: JournalEntity)

    /** 备份恢复用。date 是主键，所以这里天然是「同一天覆盖」而不是「插入第二条」 */
    @Upsert
    suspend fun upsertAll(entries: List<JournalEntity>)

    @Query("DELETE FROM journal WHERE date = :date")
    suspend fun delete(date: String)
}
