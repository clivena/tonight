package com.clivena.tonight.data

import com.clivena.tonight.data.local.JournalDao
import com.clivena.tonight.data.local.JournalEntity
import com.clivena.tonight.domain.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class LocalJournalRepository(private val dao: JournalDao) : JournalRepository {

    override fun observe(date: LocalDate): Flow<Journal?> =
        dao.observe(date.toString()).map { it?.toDomain() }

    override fun observeTimeline(): Flow<List<Journal>> =
        dao.observeTimeline().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(
        date: LocalDate,
        content: String,
        mood: Int?,
        imageName: String?
    ) {
        val key = date.toString()
        val text = content.trimEnd()

        // 什么都没写就删掉这一行。留着空行会让时间线里出现空白页，
        // 而且「一次误触就能造出一条日记」本身就是噪声。
        if (text.isEmpty() && imageName == null) {
            dao.delete(key)
            return
        }

        val now = System.currentTimeMillis()
        dao.upsert(
            JournalEntity(
                date = key,
                content = text,
                mood = mood,
                imageName = imageName,
                // 保留首次创建时间：自动保存每 800ms 就可能跑一次，
                // 每次覆盖 createdAt 会让「这篇哪天开始写的」失去意义
                createdAt = dao.createdAtOf(key) ?: now,
                updatedAt = now
            )
        )
    }

    override suspend fun remove(date: LocalDate) {
        dao.delete(date.toString())
    }

    private fun JournalEntity.toDomain() = Journal(
        date = LocalDate.parse(date),
        content = content,
        mood = mood,
        imageName = imageName
    )
}
