package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * 某天所有「还在」的事 —— 包含已完成的，排除已删除的。
     *
     * v0.2 比起 v0.1 的关键变化：这里给的是「清单」而不是「下一件」。
     * 界面需要看到全貌，才能任意勾选、任意调序。
     */
    @Query("SELECT * FROM tasks WHERE planDate = :date AND status != 'DROPPED' ORDER BY orderIndex ASC")
    fun observeActive(date: String): Flow<List<TaskEntity>>

    /** 同上，但要一次性结果 —— 调序时用来算相邻位置 */
    @Query("SELECT * FROM tasks WHERE planDate = :date AND status != 'DROPPED' ORDER BY orderIndex ASC")
    suspend fun getActive(date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT MAX(orderIndex) FROM tasks WHERE planDate = :date")
    suspend fun maxOrderIndex(date: String): Int?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query("UPDATE tasks SET content = :content, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, now: Long)

    @Query("UPDATE tasks SET orderIndex = :orderIndex, updatedAt = :now WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int, now: Long)

    @Query("SELECT * FROM tasks ORDER BY planDate DESC, orderIndex ASC")
    suspend fun getAll(): List<TaskEntity>
}
