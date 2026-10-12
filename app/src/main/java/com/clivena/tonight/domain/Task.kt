package com.clivena.tonight.domain

import java.time.LocalDate

/**
 * 领域模型。
 *
 * UI 层只认这个类型，不接触 Room 的 Entity。
 * 多一层转换，换来的是：将来加云同步、换存储引擎时，UI 层一行都不用改。
 */
data class Task(
    val id: Long,
    val planDate: LocalDate,
    val content: String,
    val orderIndex: Int,
    val status: TaskStatus
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
}

enum class TaskStatus {
    /** 待做 */
    PENDING,

    /** 已完成 */
    DONE,

    /** 已放弃。由用户主动标记，永远不会自动发生 */
    DROPPED
}
