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

    /** 某天全部待做任务，按晚上排好的顺序 */
    fun observePending(date: LocalDate): Flow<List<Task>>

    /** 某天第一件待做任务；全部做完时为 null */
    fun observeFirstPending(date: LocalDate): Flow<Task?>

    /** 某天是否还有待做任务。用于决定进「今晚」还是「明早」 */
    fun hasPending(date: LocalDate): Flow<Boolean>

    /** 某天还剩几件待做 */
    suspend fun countPending(date: LocalDate): Int

    /**
     * 添加一件要做的事。
     * @return false 表示已达当日上限（或内容为空），未写入
     */
    suspend fun add(content: String, date: LocalDate): Boolean

    suspend fun complete(id: Long)

    suspend fun drop(id: Long)

    /** 导出全部数据为纯文本 —— 本地存储唯一的后悔药 */
    suspend fun exportAll(): String
}
