package com.clivena.tonight.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
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

/**
 * 「记账」屏。
 *
 * 三个刻意的设计决定：
 *
 * 1. **汇总卡锚定真正的今天**，明细可以往回翻。如果卡片跟着明细走，
 *    翻到上周三时它会变成「上周三 / 那一周 / 那个月」，三个数字全换了意思，
 *    而用户以为自己看的是「我今天花了多少」。
 * 2. **不能翻到未来**。未来的账不存在，能翻过去只会让人怀疑数据。
 * 3. **记在什么时候就记在什么时候** —— 输入框跟着明细日期走，
 *    所以补记不需要额外再选一次日期。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repository: ExpenseRepository,
    private val today: LocalDate = LocalDate.now()
) : ViewModel() {

    /** 明细此刻在看哪一天。可以往回走 —— 补记就是靠它 */
    private val _viewDate = MutableStateFlow(today)
    val viewDate: StateFlow<LocalDate> = _viewDate.asStateFlow()

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

    /** 能不能往后翻。到今天就到底 */
    val canGoForward: StateFlow<Boolean> = _viewDate
        .map { it.isBefore(today) }
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

    fun shiftDate(days: Long) {
        val target = _viewDate.value.plusDays(days)
        if (target.isAfter(today)) return
        _viewDate.value = target
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
