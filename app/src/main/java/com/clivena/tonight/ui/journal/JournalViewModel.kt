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
import java.time.YearMonth

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

    /** 是否显示日历视图。它不是第五个 Tab，只是这一屏的另一种看法 */
    private val _calendarOpen = MutableStateFlow(false)
    val calendarOpen: StateFlow<Boolean> = _calendarOpen.asStateFlow()

    /** 日历正在看哪个月。和 viewDate 分开：翻月份不该动正在写的那一篇 */
    private val _viewMonth = MutableStateFlow(YearMonth.from(today))
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    /** 有内容的那几天。日历每格问一次「这天写过没有」 */
    val writtenDates: StateFlow<Set<LocalDate>> = repository.observeWrittenDates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )

    val canGoForward: StateFlow<Boolean> = _viewDate
        .map { it.isBefore(today) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /** 日历能不能往未来翻。不能 —— 未来的日子还没发生的，谈不上写过 */
    val canGoNextMonth: StateFlow<Boolean> = _viewMonth
        .map { it.isBefore(YearMonth.from(today)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private var saveJob: Job? = null

    /**
     * 手上这段字属于哪一天。
     *
     * 为什么不能直接拿 `_viewDate.value` 当落盘日期：切日期是**先写库再改 `_viewDate`**，
     * 而加载新一天是异步的 —— 两者中间有一个窗口，`_viewDate` 已经翻到新日期，
     * 而 `_content` 还是上一天的字。这个窗口在「逐天点箭头」时小到撞不上，
     * 但日历可以一下跳二十天，连点两格就正好落在里面，结果是**把 A 天的日记写进 B 天**。
     *
     * 所以内容的归属日期单独拿一个变量记住，它只在「真正加载完某一天的内容」时变，
     * 不跟着 `_viewDate` 走。落盘一律用它。
     */
    private var contentDate: LocalDate = today

    init {
        viewModelScope.launch {
            _viewDate.collectLatest { date ->
                val existing = repository.observe(date).first()
                _content.value = existing?.content ?: ""
                _mood.value = existing?.mood
                _imageName.value = existing?.imageName ?: null
                contentDate = date
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
        goTo(_viewDate.value.plusDays(days))
    }

    /** 从日历点了某一天。空白的天也能点 —— 补写昨天是正经需求 */
    fun openDate(date: LocalDate) {
        goTo(date)
    }

    fun openCalendar() {
        // 每次打开都把月份对准正在写的那一篇，而不是留着上次翻到的月。
        // 从日历回来再进去，看到的第一眼应该是「我现在在哪」
        _viewMonth.value = YearMonth.from(_viewDate.value)
        _calendarOpen.value = true
    }

    fun closeCalendar() {
        _calendarOpen.value = false
    }

    fun shiftMonth(months: Long) {
        val target = _viewMonth.value.plusMonths(months)
        if (target.isAfter(YearMonth.from(today))) return
        _viewMonth.value = target
    }

    /**
     * 切到某一天 —— **通往某一天的唯一路径**。
     *
     * 翻页箭头和日历格子都走这里。之前只有 [shiftDate] 会「先落盘再切」，
     * 日历是第二个入口，各写一份必然有一处忘掉那 800ms。
     * 落盘日期用 [contentDate] 而不是 `_viewDate`，理由见 [contentDate] 的注释。
     */
    private fun goTo(target: LocalDate) {
        if (target.isAfter(today)) return
        if (target == _viewDate.value) {
            // 点了当天：不用落盘，内容还在内存里，日期也没变
            _calendarOpen.value = false
            return
        }
        saveJob?.cancel()
        viewModelScope.launch {
            persist(contentDate, _content.value, _mood.value, _imageName.value)
            _viewDate.value = target
            _calendarOpen.value = false
        }
    }

    /** 离开这个屏之前调一次，把没落盘的内容补上 */
    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch {
            persist(contentDate, _content.value, _mood.value, _imageName.value)
        }
    }

    private fun scheduleSave() {
        _saved.value = false
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            persist(contentDate, _content.value, _mood.value, _imageName.value)
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
