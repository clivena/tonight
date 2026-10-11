package com.clivena.tonight.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全部数据只有这一张表。
 *
 * 不额外建「计划表」：日期可以从 planDate 派生，
 * 多一张表就多一层 join 和一致性维护，这个规模不划算。
 */
@Entity(
    tableName = "tasks",
    indices = [Index("planDate")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** yyyy-MM-dd，这件事归属哪一天 */
    val planDate: String,

    val content: String,

    /** 当晚排好的顺序 */
    val orderIndex: Int,

    /** PENDING / DONE / DROPPED */
    val status: String,

    val createdAt: Long,

    val completedAt: Long? = null,

    // ── 为将来的云同步预留 ──────────────────────────────────
    // 现在完全不用，但先建好字段。
    // Room 加字段需要写 Migration，现在建好，将来接同步时零迁移成本。
    val remoteId: String? = null,
    val updatedAt: Long = 0L
)
