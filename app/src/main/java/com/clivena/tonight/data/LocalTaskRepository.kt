package com.clivena.tonight.data

import com.clivena.tonight.data.local.TaskDao
import com.clivena.tonight.data.local.TaskEntity
import com.clivena.tonight.domain.Task
import com.clivena.tonight.domain.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class LocalTaskRepository(private val dao: TaskDao) : TaskRepository {

    override fun observeActive(date: LocalDate): Flow<List<Task>> =
        dao.observeActive(date.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun add(content: String, date: LocalDate): Boolean {
        val text = content.trim()
        if (text.isEmpty()) return false

        val dateKey = date.toString()
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

    override suspend fun reopen(id: Long) {
        dao.updateStatus(id, TaskStatus.PENDING.name, null, System.currentTimeMillis())
    }

    override suspend fun drop(id: Long) {
        dao.updateStatus(id, TaskStatus.DROPPED.name, null, System.currentTimeMillis())
    }

    override suspend fun rename(id: Long, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        dao.updateContent(id, text, System.currentTimeMillis())
    }

    override suspend fun move(id: Long, direction: MoveDirection) {
        val target = dao.getById(id) ?: return
        if (target.status == TaskStatus.DROPPED.name) return

        val siblings = dao.getActive(target.planDate)
        val index = siblings.indexOfFirst { it.id == id }
        if (index < 0) return

        val swapWith = when (direction) {
            MoveDirection.UP -> index - 1
            MoveDirection.DOWN -> index + 1
        }
        if (swapWith !in siblings.indices) return

        // 整段按位重写，而不是只交换两个 orderIndex。
        // 历史数据里 orderIndex 可能重复（比如两次 add 撞上同一个 max），
        // 交换会让重复值原样留下；逐位重写才能保证序列始终是干净的 1..n。
        val reordered = siblings.toMutableList().apply { add(swapWith, removeAt(index)) }
        val now = System.currentTimeMillis()
        reordered.forEachIndexed { i, task -> dao.updateOrderIndex(task.id, i + 1, now) }
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
}
