package com.clivena.tonight.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.Tab
import com.clivena.tonight.domain.Expense
import com.clivena.tonight.domain.LedgerTotals
import com.clivena.tonight.ui.components.RowAction
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.ui.theme.TomorrowTheme
import com.clivena.tonight.util.DateUtil
import com.clivena.tonight.util.MoneyUtil
import java.time.LocalDate
import java.time.YearMonth

/**
 * 「记账」屏 —— 始终浅色，和「今天」同族。
 *
 * 三栏里只有「规划」是深色：记账发生在花钱的当下，是白天的事。
 *
 * 屏内分两个面：「记一笔」用来写，「看花销」用来翻。
 * 分开而不是挤成一屏，因为这两件事的垂直空间需求是冲突的 ——
 * 写要的是输入框一直在手边，翻要的是尽量大的格子。
 *
 * 和其它两屏一样，这里也不引入卡片、阴影、圆角容器那一套装饰 ——
 * 数字用字号和颜色分出主次就够了。
 */
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel,
    onSelectTab: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    TomorrowTheme {
        val today = remember { LocalDate.now() }
        val mode by viewModel.mode.collectAsStateWithLifecycle()
        val viewDate by viewModel.viewDate.collectAsStateWithLifecycle()
        val viewMonth by viewModel.viewMonth.collectAsStateWithLifecycle()
        val expenses by viewModel.expenses.collectAsStateWithLifecycle()
        val totals by viewModel.totals.collectAsStateWithLifecycle()
        val monthLedger by viewModel.monthLedger.collectAsStateWithLifecycle()
        val amountInput by viewModel.amountInput.collectAsStateWithLifecycle()
        val noteInput by viewModel.noteInput.collectAsStateWithLifecycle()
        val error by viewModel.error.collectAsStateWithLifecycle()
        val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
        val canGoNextMonth by viewModel.canGoNextMonth.collectAsStateWithLifecycle()
        val keyboard = LocalSoftwareKeyboardController.current

        var editingId by remember { mutableStateOf<Long?>(null) }
        var draftAmount by remember { mutableStateOf("") }
        var draftNote by remember { mutableStateOf("") }

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Spacer(Modifier.height(48.dp))

                TopTabs(active = Tab.LEDGER, onSelect = onSelectTab)

                Spacer(Modifier.height(20.dp))

                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "记账",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(14.dp))

                    ModeSwitch(mode = mode, onSelect = viewModel::switchMode)
                }

                if (mode == LedgerMode.RECORD) {
                    RecordBody(
                        today = today,
                        viewDate = viewDate,
                        expenses = expenses,
                        totals = totals,
                        amountInput = amountInput,
                        noteInput = noteInput,
                        error = error,
                        canGoForward = canGoForward,
                        editingId = editingId,
                        draftAmount = draftAmount,
                        draftNote = draftNote,
                        onDraftAmountChange = { draftAmount = it },
                        onDraftNoteChange = { draftNote = it },
                        onStartEdit = { expense ->
                            editingId = expense.id
                            draftAmount = MoneyUtil.toPlainInput(expense.amountCents)
                            draftNote = expense.note
                        },
                        onCommitEdit = { expense ->
                            val cents = MoneyUtil.parseToCents(draftAmount)
                            if (cents != null && cents > 0L) {
                                viewModel.edit(expense, cents, draftNote)
                                editingId = null
                                keyboard?.hide()
                            }
                        },
                        onCancelEdit = { editingId = null },
                        onRemove = viewModel::remove,
                        onShiftDate = viewModel::shiftDate,
                        onAmountChange = viewModel::onAmountChange,
                        onNoteChange = viewModel::onNoteChange,
                        onAdd = {
                            viewModel.addEntry()
                            keyboard?.hide()
                        }
                    )
                } else {
                    BrowseBody(
                        today = today,
                        anchorTotals = totals,
                        monthLabel = DateUtil.monthLabel(viewMonth),
                        monthTotalCents = monthLedger.totalCents,
                        monthDailyTotals = monthLedger.dailyTotals,
                        peakDayCents = monthLedger.peakDayCents,
                        activeDays = monthLedger.activeDays,
                        viewMonth = viewMonth,
                        canGoNextMonth = canGoNextMonth,
                        onShiftMonth = viewModel::shiftMonth,
                        onPickDate = viewModel::openDate
                    )
                }
            }
        }
    }
}

/**
 * 「记一笔」：大数字 + 当天明细 + 输入框。
 *
 * 汇总卡按老样子锚定真正的今天，不跟着明细翻页走 ——
 * 见 LedgerViewModel 类注释第 1 条。
 */
@Composable
private fun ColumnScope.RecordBody(
    today: LocalDate,
    viewDate: LocalDate,
    expenses: List<Expense>,
    totals: LedgerTotals,
    amountInput: String,
    noteInput: String,
    error: String?,
    canGoForward: Boolean,
    editingId: Long?,
    draftAmount: String,
    draftNote: String,
    onDraftAmountChange: (String) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onStartEdit: (Expense) -> Unit,
    onCommitEdit: (Expense) -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (Long) -> Unit,
    onShiftDate: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))

        Summary(totals)

        Spacer(Modifier.height(18.dp))

        HairLine()

        Spacer(Modifier.height(12.dp))

        // 明细的日期导航。「←」可以一直往回走去看历史，
        // 「→」到今天为止 —— 未来的账不存在。
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowAction("←", enabled = true) { onShiftDate(-1) }
            Spacer(Modifier.width(10.dp))
            Text(
                text = DateUtil.labelFor(viewDate, today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(10.dp))
            RowAction("→", enabled = canGoForward) { onShiftDate(1) }
        }
    }

    Spacer(Modifier.height(6.dp))

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (expenses.isEmpty()) {
            item {
                Text(
                    text = if (viewDate == today) {
                        "今天还没记。花了就记一笔。"
                    } else {
                        "这一天没有记录。"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(expenses, key = { it.id }) { expense ->
            LedgerRow(
                expense = expense,
                editing = editingId == expense.id,
                draftAmount = draftAmount,
                draftNote = draftNote,
                onDraftAmountChange = onDraftAmountChange,
                onDraftNoteChange = onDraftNoteChange,
                onStartEdit = { onStartEdit(expense) },
                onCommitEdit = { onCommitEdit(expense) },
                onCancelEdit = onCancelEdit,
                onDelete = {
                    onCancelEdit()
                    onRemove(expense.id)
                }
            )
        }
    }

    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = "记在 ${DateUtil.labelFor(viewDate, today)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = amountInput,
                onValueChange = onAmountChange,
                modifier = Modifier.width(118.dp),
                placeholder = {
                    Text(
                        text = "金额",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(Modifier.width(8.dp))

            OutlinedTextField(
                value = noteInput,
                onValueChange = onNoteChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "备注（可不填）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() })
            )
        }

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onAdd, enabled = amountInput.isNotBlank()) {
                Text("记一笔", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
}

/**
 * 「看花销」：月份导航 + 月历 + 本月的合计。
 *
 * 顶部那行小字是**此刻**的今日 / 本周 / 本月，锚定真正的今天。
 * 它和下面的月历分属两个时间维度，所以刻意做成灰字 ——
 * 好和月历下面的「合计」（正在看的那个月）区分开：
 * 翻到 3 月时，如果上面那行也跟着变成 3 月，
 * 那「今天花了多少」就永远看不到了。
 */
@Composable
private fun ColumnScope.BrowseBody(
    today: LocalDate,
    anchorTotals: LedgerTotals,
    monthLabel: String,
    monthTotalCents: Long,
    monthDailyTotals: Map<LocalDate, Long>,
    peakDayCents: Long,
    activeDays: Int,
    viewMonth: YearMonth,
    canGoNextMonth: Boolean,
    onShiftMonth: (Long) -> Unit,
    onPickDate: (LocalDate) -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "今日 ${MoneyUtil.format(anchorTotals.todayCents)}" +
                " · 本周 ${MoneyUtil.format(anchorTotals.weekCents)}" +
                " · 本月 ${MoneyUtil.format(anchorTotals.monthCents)}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(Modifier.height(18.dp))

        HairLine()

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RowAction("←", enabled = true) { onShiftMonth(-1) }
            Spacer(Modifier.width(10.dp))
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(10.dp))
            RowAction("→", enabled = canGoNextMonth) { onShiftMonth(1) }
        }

        Spacer(Modifier.height(16.dp))

        MonthGrid(
            month = viewMonth,
            dailyTotals = monthDailyTotals,
            peakDayCents = peakDayCents,
            today = today,
            onPickDate = onPickDate
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "合计 ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = MoneyUtil.format(monthTotalCents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (activeDays > 0) {
                Text(
                    text = " · 有花销 $activeDays 天",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * 「记一笔 / 看花销」的二级切换。
 *
 * 做成比顶部 TopTabs 小一号的同款（小字、更短的下划线），
 * 一眼能看出它是同一屏里的第二层，而不是第四个 Tab。
 */
@Composable
private fun ModeSwitch(mode: LedgerMode, onSelect: (LedgerMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModeItem("记一笔", mode == LedgerMode.RECORD) { onSelect(LedgerMode.RECORD) }
        Spacer(Modifier.width(18.dp))
        ModeItem("看花销", mode == LedgerMode.BROWSE) { onSelect(LedgerMode.BROWSE) }
    }
}

@Composable
private fun ModeItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .height(1.5.dp)
                .width(12.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

@Composable
private fun HairLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

/**
 * 汇总卡。
 *
 * 「今日」是主角，字号和颜色都给它；周和月是陪衬，一行小字并列。
 * 刻意不做成三个等大的数字 —— 那样等于没有主次，
 * 而用户扫一眼这一屏，最想知道的永远是今天花了多少。
 */
@Composable
private fun Summary(totals: LedgerTotals) {
    Column {
        Text(
            text = "今日",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = MoneyUtil.format(totals.todayCents),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                lineHeight = 38.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周 ${MoneyUtil.format(totals.weekCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = "本月 ${MoneyUtil.format(totals.monthCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * 一行账：备注在左、金额在右，像真的账本。
 *
 * 两个态：
 * - 常态：整行可点（进入编辑），右侧「×」删除
 * - 编辑态：金额和备注就地变输入框，右侧「✓」提交、「×」放弃这次修改
 *   「×」在两种态下都表示「放弃当前这个动作」，语义是一致的
 */
@Composable
private fun LedgerRow(
    expense: Expense,
    editing: Boolean,
    draftAmount: String,
    draftNote: String,
    onDraftAmountChange: (String) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit
) {
    if (editing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draftAmount,
                onValueChange = onDraftAmountChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .width(72.dp)
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.width(8.dp))

            BasicTextField(
                value = draftNote,
                onValueChange = onDraftNoteChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.width(6.dp))

            RowAction("✓", enabled = true, onClick = onCommitEdit)
            RowAction("×", enabled = true, onClick = onCancelEdit)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onStartEdit)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = expense.note,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )

            Text(
                text = MoneyUtil.format(expense.amountCents),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )

            Spacer(Modifier.width(6.dp))

            RowAction("×", enabled = true, onClick = onDelete)
        }
    }
}
