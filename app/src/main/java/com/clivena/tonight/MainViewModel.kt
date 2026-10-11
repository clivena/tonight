package com.clivena.tonight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App 此刻该显示哪一屏 */
enum class Screen {
    /** 今晚屏：规划 */
    EVENING,

    /** 明早屏：执行 */
    MORNING,

    /** 今天的事全做完了 */
    ALL_DONE,

    /** 今晚的规划保存完了 */
    GOOD_NIGHT
}

class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _screen = MutableStateFlow<Screen?>(null)

    /** null 表示还在判断（第一帧） */
    val screen: StateFlow<Screen?> = _screen.asStateFlow()

    /** 今晚屏要规划哪一天 */
    val planDate: LocalDate = DateUtil.planDateFor()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val pending = repository.observePending(today).first()
            // 今天还有没做完的 → 先去做完；否则 → 规划
            _screen.value = if (pending.isEmpty()) Screen.EVENING else Screen.MORNING
        }
    }

    fun onPlanSaved() {
        _screen.value = Screen.GOOD_NIGHT
    }

    fun onAllDone() {
        _screen.value = Screen.ALL_DONE
    }

    fun onPlan() {
        _screen.value = Screen.EVENING
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
