package com.clivena.tonight.data

import com.clivena.tonight.domain.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 数据层对 UI 的唯一出口。
 *
 * 现在只有本地实现。将来加云同步时，
 * 新增一个实现（或用装饰器组合本地与远端），UI 层不需要任何改动。
 */
interface TaskRepository {

    /**
     * 某天全部「还在」的事，含已完成、不含已删除。
     *
     * 顺序就是用户自己排的顺序 —— 界面不再做二次排序，
     * 免得勾掉一件列表就跳一下。
     */
    fun observeActive(date: LocalDate): Flow<List<Task>>

    /**
     * 添加一件要做的事。
     *
     * v0.2 起没有数量上限 —— 只校验内容非空。
     * @return false 表示内容为空，未写入
     */
    suspend fun add(content: String, date: LocalDate): Boolean

    suspend fun complete(id: Long)

    /** 取消完成，回到待做。清单上再点一次复选框就是它 */
    suspend fun reopen(id: Long)

    /** 从清单里去掉。软删除，不占位也不再显示 */
    suspend fun drop(id: Long)

    /** 改掉某件事的文字。空内容会被忽略 */
    suspend fun rename(id: Long, content: String)

    /** 在自己所属那天的清单里上移或下移一位 */
    suspend fun move(id: Long, direction: MoveDirection)
}

enum class MoveDirection { UP, DOWN }
