package com.clivena.tonight.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一篇日记。**一天一篇**，`date` 直接做主键。
 *
 * 用日期当主键而不是自增 id，是因为「多篇」这个可能性本身就被否掉了：
 * 一天写两段不叫两篇日记，叫一段写长点。主键选得对，业务约束就不用靠代码守。
 *
 * 这是本工程第一张**不是从 tasks 派生**的表 —— 任务是「要做的事」，
 * 日记是「发生过的事」，两者唯一的共同点是都按日期归属。
 */
@Entity(tableName = "journal")
data class JournalEntity(

    /** yyyy-MM-dd */
    @PrimaryKey
    val date: String,

    /** 正文，可为空串（只配了图没写字是合法的） */
    val content: String,

    /** 心情 1..5，null = 没填。刻意不做成必填 —— 强制记录情绪会让人不想打开 */
    val mood: Int?,

    /**
     * 配图文件名（不是路径）。
     *
     * 绝对路径里带包名和用户 id，换设备或恢复备份就会失效；
     * 文件名 + 每次现算目录，才是能跨设备成立的写法。
     */
    val imageName: String?,

    val createdAt: Long,
    val updatedAt: Long
)
