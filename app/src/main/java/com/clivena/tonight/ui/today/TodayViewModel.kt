package com.clivena.tonight.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.domain.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 今日清单。
 *
 * v0.1 的明早屏只递「下一件」，做不了第一件就整天卡死。
 * v0.2 换成一份可以任意勾选的清单 —— 顺序由你自己排，勾选不按顺序。
 */
class TodayViewModel(
    private val repository: TaskRepository,
    private val date: LocalDate
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.observeActive(date)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 一个复选框管两种语义：
     * 没做完的勾上 → 完成；做完了再点一下 → 退回待做。
     *
     * 允许取消是刻意的 —— 勾错了没得救的清单，用两次就不敢用了。
     */
    fun toggle(task: Task) {
        viewModelScope.launch {
            if (task.isDone) repository.reopen(task.id) else repository.complete(task.id)
        }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            date: LocalDate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TodayViewModel(repository, date) as T
        }
    }
}
