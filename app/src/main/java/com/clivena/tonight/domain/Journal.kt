package com.clivena.tonight.domain

import java.time.LocalDate

/**
 * 一篇日记（领域模型）。
 *
 * 和 [com.clivena.tonight.data.local.JournalEntity] 分开的理由和 Task / Expense 一样：
 * UI 只认这个，换存储引擎或加云同步时上层不用改。
 */
data class Journal(
    val date: LocalDate,
    val content: String,
    val mood: Int?,
    val imageName: String?
) {
    /** 有字或有图才算「写过」。只有心情不算 —— 光点了心情没有内容，不该进时间线 */
    val isEmpty: Boolean get() = content.isBlank() && imageName == null
}
