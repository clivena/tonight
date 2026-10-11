package com.clivena.tonight.ui.evening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.LocalTaskRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.domain.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class EveningViewModel(
    private val repository: TaskRepository,
    private val planDate: LocalDate
) : ViewModel() {

    /** 已经放进去的事，顺序就是此刻排好的顺序 */
    val tasks: StateFlow<List<Task>> = repository.observePending(planDate)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /** 撞到上限了 —— 用来显示那句「够了。真的够了。」 */
    private val _limitReached = MutableStateFlow(false)
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    fun onInputChange(value: String) {
        _input.value = value
        if (_limitReached.value) _limitReached.value = false
    }

    fun addTask() {
        val text = _input.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            if (repository.add(text, planDate)) {
                _input.value = ""
            } else {
                _limitReached.value = true
            }
        }
    }

    fun removeTask(id: Long) {
        viewModelScope.launch { repository.drop(id) }
    }

    companion object {
        val MAX_TASKS: Int = LocalTaskRepository.MAX_TASKS_PER_DAY

        fun factory(
            repository: TaskRepository,
            planDate: LocalDate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EveningViewModel(repository, planDate) as T
        }
    }
}
