package com.clivena.tonight.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE planDate = :date AND status = 'PENDING' ORDER BY orderIndex ASC")
    fun observePending(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE planDate = :date AND status = 'PENDING' ORDER BY orderIndex ASC LIMIT 1")
    fun observeFirstPending(date: String): Flow<TaskEntity?>

    @Query("SELECT COUNT(*) FROM tasks WHERE planDate = :date AND status = 'PENDING'")
    suspend fun countPending(date: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE planDate = :date")
    suspend fun countAll(date: String): Int

    @Query("SELECT MAX(orderIndex) FROM tasks WHERE planDate = :date")
    suspend fun maxOrderIndex(date: String): Int?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long?, now: Long)

    @Query("SELECT * FROM tasks ORDER BY planDate DESC, orderIndex ASC")
    suspend fun getAll(): List<TaskEntity>
}
