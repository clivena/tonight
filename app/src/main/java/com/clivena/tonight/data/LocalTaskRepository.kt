package com.clivena.tonight.data

import com.clivena.tonight.data.local.TaskDao
import com.clivena.tonight.data.local.TaskEntity
import com.clivena.tonight.domain.Task
import com.clivena.tonight.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class LocalTaskRepository(private val dao: TaskDao) : TaskRepository {

    override fun observePending(date: LocalDate): Flow<List<Task>> =
        dao.observePending(date.toString()).map { list -> list.map { it.toDomain() } }

    override fun observeFirstPending(date: LocalDate): Flow<Task?> =
        dao.observeFirstPending(date.toString()).map { it?.toDomain() }

    override fun hasPending(date: LocalDate): Flow<Boolean> =
        dao.observePending(date.toString()).map { it.isNotEmpty() }

    override suspend fun countPending(date: LocalDate): Int =
        dao.countPending(date.toString())

    override suspend fun add(content: String, date: LocalDate): Boolean {
        val text = content.trim()
        if (text.isEmpty()) return false

        val dateKey = date.toString()
        if (dao.countAll(dateKey) >= MAX_TASKS_PER_DAY) return false

        val now = System.currentTimeMillis()
        dao.insert(
            TaskEntity(
                planDate = dateKey,
                content = text,
                orderIndex = (dao.maxOrderIndex(dateKey) ?: 0) + 1,
                status = TaskStatus.PENDING.name,
                createdAt = now,
                updatedAt = now
            )
        )
        return true
    }

    override suspend fun complete(id: Long) {
        val now = System.currentTimeMillis()
        dao.updateStatus(id, TaskStatus.DONE.name, now, now)
    }

    override suspend fun drop(id: Long) {
        dao.updateStatus(id, TaskStatus.DROPPED.name, null, System.currentTimeMillis())
    }

    override suspend fun exportAll(): String {
        val all = dao.getAll()
        if (all.isEmpty()) return "（还没有任何记录）"

        val builder = StringBuilder()
        builder.appendLine("# 今晚 · 数据导出")
        builder.appendLine()
        all.groupBy { it.planDate }.forEach { (date, tasks) ->
            builder.appendLine("## $date")
            tasks.forEach { task ->
                builder.appendLine("${task.status.toMark()} ${task.content}")
            }
            builder.appendLine()
        }
        return builder.toString()
    }

    private fun String.toMark(): String = when (this) {
        TaskStatus.DONE.name -> "[x]"
        TaskStatus.DROPPED.name -> "[-]"
        else -> "[ ]"
    }

    private fun TaskEntity.toDomain() = Task(
        id = id,
        planDate = LocalDate.parse(planDate),
        content = content,
        orderIndex = orderIndex,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING)
    )

    companion object {
        /**
         * 每天最多几件事。
         *
         * 超过 5 件，早上就不会去选了 —— 那和没有这个 App 是一个结果。
         * 这个数字是整个产品的关键约束，不是随手定的。
         */
        const val MAX_TASKS_PER_DAY = 5
    }
}
