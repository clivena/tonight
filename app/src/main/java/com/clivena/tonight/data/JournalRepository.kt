package com.clivena.tonight.data

import com.clivena.tonight.domain.Journal
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 日记数据的唯一出口。
 *
 * 和 TaskRepository / ExpenseRepository 并列，第三套独立的仓储：
 * 日记既不是待办也不是账目，把它塞进任何一边都会让那边的模型变脏。
 */
interface JournalRepository {

    /** 某一天的那一篇，没有就是 null */
    fun observe(date: LocalDate): Flow<Journal?>

    /** 所有写过的天，新的在前 */
    fun observeTimeline(): Flow<List<Journal>>

    /**
     * 保存。**同时承担「清空即删除」的语义** ——
     * 正文清空、图也删了，这一篇就不该再存在，
     * 否则时间线里会留下翻过去一片空白的页。
     *
     * 已有的 createdAt 会被保留：反复自动保存不该改写「哪天开始写的」。
     */
    suspend fun save(date: LocalDate, content: String, mood: Int?, imageName: String?)

    suspend fun remove(date: LocalDate)
}
