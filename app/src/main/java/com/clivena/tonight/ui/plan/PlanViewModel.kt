package com.clivena.tonight.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.MoveDirection
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.domain.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 「规划」屏。
 *
 * v0.1 的今晚屏一旦保存就走到头了（只剩一句「晚安」）。
 * v0.2 把它变成随时能进、随时能改的一面：
 * 日期可切、文字可改、顺序可调、条目可删，而且没有条数上限。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val repository: TaskRepository,
    initialDate: LocalDate
) : ViewModel() {

    private val _planDate = MutableStateFlow(initialDate)

    /** 此刻在规划哪一天。默认值只是默认值，界面上随时可推翻 */
    val planDate: StateFlow<LocalDate> = _planDate.asStateFlow()

    val tasks: StateFlow<List<Task>> = _planDate
        .flatMapLatest { repository.observeActive(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    fun selectDate(date: LocalDate) {
        if (date != _planDate.value) _planDate.value = date
    }

    fun onInputChange(value: String) {
        _input.value = value
    }

    fun addTask() {
        val text = _input.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            if (repository.add(text, _planDate.value)) _input.value = ""
        }
    }

    fun rename(id: Long, content: String) {
        viewModelScope.launch { repository.rename(id, content) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.drop(id) }
    }

    fun moveUp(id: Long) {
        viewModelScope.launch { repository.move(id, MoveDirection.UP) }
    }

    fun moveDown(id: Long) {
        viewModelScope.launch { repository.move(id, MoveDirection.DOWN) }
    }

    fun toggle(task: Task) {
        viewModelScope.launch {
            if (task.isDone) repository.reopen(task.id) else repository.complete(task.id)
        }
    }

    companion object {
        fun factory(
            repository: TaskRepository,
            initialDate: LocalDate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlanViewModel(repository, initialDate) as T
        }
    }
}
