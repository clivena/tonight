package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
        "SELECT * FROM journal WHERE content != '' OR imageName IS NOT NULL " +
            "ORDER BY date DESC"
    )
    fun observeTimeline(): Flow<List<JournalEntity>>

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
