package com.clivena.tonight.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.JournalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 「日记」屏。
 *
 * 三个刻意的设计决定：
 *
 * 1. **没有保存按钮**。写日记时手一停就要点一下保存，那个动作本身就打断思路。
 *    这里改成停笔 800ms 自动存 —— 用户永远不用想「存了没」。
 * 2. **一天一篇**。想写就写长的，不提供「新开一条」的选择。
 *    一天两段不叫两篇日记，叫一段写长点。
 * 3. **翻页前先落盘**。切日期时会先取消待执行的自动保存、立刻写一次，
 *    再把日期切走 —— 否则「写完马上翻走」会丢最后那 800ms 的内容。
 */
class JournalViewModel(
    private val repository: JournalRepository,
    private val today: LocalDate = LocalDate.now()
) : ViewModel() {

    private val _viewDate = MutableStateFlow(today)
    val viewDate: StateFlow<LocalDate> = _viewDate.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _mood = MutableStateFlow<Int?>(null)
    val mood: StateFlow<Int?> = _mood.asStateFlow()

    private val _imageName = MutableStateFlow<String?>(null)
    val imageName: StateFlow<String?> = _imageName.asStateFlow()

    /** 这一篇此刻是否已经落盘。界面上只显示一行小字，不打扰写作 */
    private val _saved = MutableStateFlow(true)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val canGoForward: StateFlow<Boolean> = _viewDate
        .map { it.isBefore(today) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            _viewDate.collectLatest { date ->
                val existing = repository.observe(date).first()
                _content.value = existing?.content ?: ""
                _mood.value = existing?.mood
                _imageName.value = existing?.imageName ?: null
                _saved.value = true
            }
        }
    }

    fun onContentChange(text: String) {
        _content.value = text
        scheduleSave()
    }

    fun setMood(value: Int?) {
        // 再点一次同一档就取消 —— 心情不是必答题，允许反悔
        _mood.value = if (_mood.value == value) null else value
        scheduleSave()
    }

    /** 配图换上了（[name] 为 null 表示移除）。文件的增删由界面层负责 */
    fun setImage(name: String?) {
        _imageName.value = name
        scheduleSave()
    }

    fun shiftDate(days: Long) {
        val target = _viewDate.value.plusDays(days)
        if (target.isAfter(today)) return
        val leaving = _viewDate.value
        viewModelScope.launch {
            // 先把手上这篇写死再走，否则最后 800ms 的内容会跟着页面一起消失
            saveJob?.cancel()
            persist(leaving, _content.value, _mood.value, _imageName.value)
            _viewDate.value = target
        }
    }

    /** 离开这个屏之前调一次，把没落盘的内容补上 */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(_viewDate.value, _content.value, _mood.value, _imageName.value)
        }
    }

    private fun scheduleSave() {
        _saved.value = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist(_viewDate.value, _content.value, _mood.value, _imageName.value)
        }
    }

    private suspend fun persist(date: LocalDate, content: String, mood: Int?, image: String?) {
        repository.save(date, content, mood, image)
        _saved.value = true
    }

    companion object {
        private const val SAVE_DELAY_MS = 800L

        fun factory(journalRepository: JournalRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    JournalViewModel(journalRepository) as T
            }
    }
}
