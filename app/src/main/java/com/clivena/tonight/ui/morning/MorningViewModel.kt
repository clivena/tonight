package com.clivena.tonight.ui.morning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.domain.Task
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MorningViewModel(
    private val repository: TaskRepository,
    private val today: LocalDate
) : ViewModel() {

    /**
     * 只暴露「当前这一件」。
     *
     * 不给列表、不给剩余总数 —— 数量本身也会变成压力，
     * 而且会让人下意识地评估「来不来得及」。那正是这个 App 要消灭的动作。
     */
    val currentTask: StateFlow<Task?> = repository.observeFirstPending(today)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _allDone = Channel<Unit>(Channel.BUFFERED)
    val allDone: Flow<Unit> = _allDone.receiveAsFlow()

    fun complete(taskId: Long) {
        viewModelScope.launch {
            repository.complete(taskId)
            if (repository.countPending(today) == 0) {
                _allDone.send(Unit)
            }
        }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            today: LocalDate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MorningViewModel(repository, today) as T
        }
    }
}
