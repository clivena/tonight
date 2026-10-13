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

    /** 金额看不懂时说一句，而不是静默吞掉这次输入 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun switchMode(mode: LedgerMode) {
        _mode.value = mode
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

    fun addEntry() {
        val cents = MoneyUtil.parseToCents(_amountInput.value)
        if (cents == null || cents <= 0L) {
            _error.value = "金额写清楚一点，比如 32 或 32.5"
            return
        }
        val note = _noteInput.value
        val date = _viewDate.value
        viewModelScope.launch {
            if (repository.add(cents, note, date)) {
                _amountInput.value = ""
                _noteInput.value = ""
                _error.value = null
            }
        }
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
