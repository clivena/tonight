package com.clivena.tonight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.domain.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App 的两个面：看今天的，和排以后要做的 */
enum class Tab { TODAY, PLAN }

/**
 * 只做一件事：决定此刻显示哪个 Tab。
 *
 * v0.1 里这里是个四屏枚举，还有一条「规划完只能看晚安」的单行道。
 * v0.2 拆掉了：两个 Tab 常驻可互达，任何时刻都能从「今天」走到「规划」再走回来。
 */
class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _tab = MutableStateFlow<Tab?>(null)

    /** null 表示还在读数据库判断落点（第一帧，先留白） */
    val tab: StateFlow<Tab?> = _tab.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val hasPending = repository.observeActive(today).first()
                .any { it.status == TaskStatus.PENDING }
            // 今天还有没做完的 → 先看今天；否则 → 直接去规划
            _tab.value = if (hasPending) Tab.TODAY else Tab.PLAN
        }
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    companion object {
        fun factory(repository: TaskRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(repository) as T
            }
    }
}
