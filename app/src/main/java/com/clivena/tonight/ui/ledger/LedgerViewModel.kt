package com.clivena.tonight.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
import com.clivena.tonight.domain.MonthLedger
import com.clivena.tonight.util.MoneyUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** 记账屏的两个面：往里写、往回翻 */
enum class LedgerMode { RECORD, BROWSE }

/**
 * 「记账」屏。
 *
 * 五个刻意的设计决定：
 *
 * 1. **汇总卡锚定真正的今天**，明细可以往回翻。如果卡片跟着明细走，
 *    翻到上周三时它会变成「上周三 / 那一周 / 那个月」，三个数字全换了意思，
 *    而用户以为自己看的是「我今天花了多少」。
 * 2. **不能翻到未来**。未来的账不存在，能翻过去只会让人怀疑数据。
 * 3. **记在什么时候就记在什么时候** —— 输入框跟着明细日期走，
 *    所以补记不需要额外再选一次日期。
 * 4. **看花销的月份不会被模式切换重置**。翻到 3 月看两眼、切回记一笔、
 *    再切回来，看到的还是 3 月 —— 否则每切一次就跳回本月，等于没法用。
 *    日期动了（明细翻页、点月历某天）才会带动月份。
 * 5. **月历点某一天，直接落到那天的明细上**。这是「翻完账顺手补一笔」
 *    的自然路径，所以点完不只是高亮，而是把输入框也带过去。
 * 6. **备注优先给标签，打字是备选**。记账的真实分布是少数名目反复出现
 *    （午饭、通勤、咖啡），把高频的那几个抬到一键，比优化输入法有用得多；
 *    而标签是从历史账目里算出来的，用户一次都不用维护。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: ExpenseRepository,
    private val today: LocalDate = LocalDate.now()
) : ViewModel() {

    private val todayMonth: YearMonth = YearMonth.from(today)

    private val _mode = MutableStateFlow(LedgerMode.RECORD)
    val mode: StateFlow<LedgerMode> = _mode.asStateFlow()

    /** 明细此刻在看哪一天。可以往回走 —— 补记就是靠它 */
    private val _viewDate = MutableStateFlow(today)
    val viewDate: StateFlow<LocalDate> = _viewDate.asStateFlow()

    /** 月历此刻在看哪个月 */
    private val _viewMonth = MutableStateFlow(todayMonth)
    val viewMonth: StateFlow<YearMonth> = _viewMonth.asStateFlow()

    val expenses: StateFlow<List<Expense>> = _viewDate
        .flatMapLatest { repository.observeDay(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 三个口径的合计。锚点固定是今天，见类注释第 1 条 */
    val totals: StateFlow<LedgerTotals> = repository.observeTotals(today)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LedgerTotals()
        )

    /** 月历要的那一个月：每天多少、合计多少 */
    val monthLedger: StateFlow<MonthLedger> = _viewMonth
        .flatMapLatest { repository.observeMonth(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonthLedger(todayMonth)
        )

    /** 能不能往后翻。到今天就到底 */
    val canGoForward: StateFlow<Boolean> = _viewDate
        .map { it.isBefore(today) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /** 能不能往后翻月。到当月就到底 */
    val canGoNextMonth: StateFlow<Boolean> = _viewMonth
        .map { it.isBefore(todayMonth) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private val _amountInput = MutableStateFlow("")
    val amountInput: StateFlow<String> = _amountInput.asStateFlow()

    private val _noteInput = MutableStateFlow("")
    val noteInput: StateFlow<String> = _noteInput.asStateFlow()

    /**
     * 常用备注标签，从历史账目里算出来的，用户不用维护。
     *
     * 没有记录时是空列表 —— 界面那边用一组兜底标签顶上，
     * 否则第一次打开看到空一条，用户根本不知道这里有东西可用。
     */
    val noteChips: StateFlow<List<String>> = repository.observeNoteChips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 此刻选中的标签；null 表示没选 */
    private val _selectedNote = MutableStateFlow<String?>(null)
    val selectedNote: StateFlow<String?> = _selectedNote.asStateFlow()

    /** 「自己写」是否展开。展开时以输入框为准，标签选中态让位 */
    private val _customNoteOpen = MutableStateFlow(false)
    val customNoteOpen: StateFlow<Boolean> = _customNoteOpen.asStateFlow()

    /** 金额看不懂时说一句，而不是静默吞掉这次输入 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun switchMode(mode: LedgerMode) {
        _mode.value = mode
    }

    /**
     * 点标签：选中 / 取消选中。
     *
     * 再点一下取消，是因为「点错了想反悔」的比重记一笔还常见 ——
     * 何况选中态本身要能表达「这一笔不写备注」。
     */
    fun toggleNote(note: String) {
        if (_selectedNote.value == note) {
            _selectedNote.value = null
            return
        }
        _selectedNote.value = note
        // 换成标签就说明不打算自己写了，收起输入框免得两个来源打架
        _customNoteOpen.value = false
        _noteInput.value = ""
    }

    fun openCustomNote() {
        _customNoteOpen.value = true
        _selectedNote.value = null
    }

    fun closeCustomNote() {
        _customNoteOpen.value = false
        _noteInput.value = ""
    }

    fun shiftDate(days: Long) {
        val target = _viewDate.value.plusDays(days)
        if (target.isAfter(today)) return
        _viewDate.value = target
        // 日期一动月份就跟着走 —— 保持「明细在哪天，月历就在哪个月」
        _viewMonth.value = YearMonth.from(target)
    }

    fun shiftMonth(months: Long) {
        val target = _viewMonth.value.plusMonths(months)
        if (target.isAfter(todayMonth)) return
        _viewMonth.value = target
    }

    /** 点月历上的某一天：落到那天的明细上，顺手可以补一笔 */
    fun openDate(date: LocalDate) {
        if (date.isAfter(today)) return
        _viewDate.value = date
        _viewMonth.value = YearMonth.from(date)
        _mode.value = LedgerMode.RECORD
    }

    fun onAmountChange(value: String) {
        _amountInput.value = value
        if (_error.value != null) _error.value = null
    }

    fun onNoteChange(value: String) {
        _noteInput.value = value
    }

    /**
     * 记一笔。@return false 表示金额没看懂，这次输入被退回。
     *
     * 返回值只反映「校验是否通过」，不反映写库结果 ——
     * 界面拿它决定要不要收键盘：写错了就把键盘留着，
     * 让用户直接改，而不是收起来再点一次输入框。
     */
    fun addEntry(): Boolean {
        val cents = MoneyUtil.parseToCents(_amountInput.value)
        if (cents == null || cents <= 0L) {
            _error.value = "金额写清楚一点，比如 32 或 32.5"
            return false
        }
        // 备注只有一个来源：自己写的内容，或选中的标签。
        // 两者互斥（见 toggleNote / openCustomNote），所以这里不需要仲裁优先级。
        val note = if (_customNoteOpen.value) _noteInput.value else (_selectedNote.value ?: "")
        val date = _viewDate.value
        viewModelScope.launch {
            if (repository.add(cents, note, date)) {
                // 记完清场。刻意不保留上一次选中的标签 ——
                // 保留虽然能连着记同一类，但下一次要记别的时，
                // 得先发现「还选着旧的」再取消，多了一步且容易记错备注。
                _amountInput.value = ""
                _noteInput.value = ""
                _selectedNote.value = null
                _customNoteOpen.value = false
                _error.value = null
            }
        }
        return true
    }

    fun edit(expense: Expense, amountCents: Long, note: String) {
        viewModelScope.launch { repository.edit(expense, amountCents, note) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    companion object {
        fun factory(expenseRepository: ExpenseRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LedgerViewModel(expenseRepository) as T
            }
    }
}
