package com.clivena.tonight.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一笔账。
 *
 * 独立于 tasks 的第二章表 —— 记账和任务是两件事，
 * 表不合并、字段不复用，谁也不因为对方变复杂。
 */
@Entity(
    tableName = "expenses",
    indices = [Index("date")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** yyyy-MM-dd，这笔账算在哪一天 */
    val date: String,

    /**
     * 金额，单位「分」。
     *
     * 刻意不用 Double / Float：二进制浮点表示不了 0.1，
     * 单笔看不出来，累加到月总额就会和实际对不上。
     * 钱必须是整数 —— 这条约束从数据库一路贯穿到界面。
     */
    val amountCents: Long,

    /** 备注，可为空串。不填也能存 —— 记账的输入负担要低 */
    val note: String,

    val createdAt: Long,

    // ── 为将来的云同步预留 ──────────────────────────────────
    val remoteId: String? = null,
    val updatedAt: Long = 0L
)
