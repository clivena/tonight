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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
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
 * 和其它两屏一样，这里也不引入卡片、阴影、圆角大容器那一套装饰 ——
 * 数字用字号和颜色分出主次就够了。
 *
 * v0.5 的两处结构性改动：
 * 1. **备注从「打字」变成「点选」**。历史备注在底部排成可点的标签，
 *    记账的主路径（点标签 → 输数字 → 记）全程不需要切换输入法。
 * 2. **删除从常态行收进编辑态**。账目删除是不可撤销的，
 *    常驻一个「×」在每行右边，误触的代价和收益完全不成比例。
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
        val noteChips by viewModel.noteChips.collectAsStateWithLifecycle()
        val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()
        val customNoteOpen by viewModel.customNoteOpen.collectAsStateWithLifecycle()
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

                Spacer(Modifier.height(16.dp))

                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "记账",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(16.dp))

                    ModeSwitch(mode = mode, onSelect = viewModel::switchMode)
                }

                if (mode == LedgerMode.RECORD) {
                    RecordBody(
                        today = today,
                        viewDate = viewDate,
                        expenses = expenses,
                        totals = totals,
                        input = InputState(
                            amount = amountInput,
                            note = noteInput,
                            chips = noteChips,
                            selectedNote = selectedNote,
                            customOpen = customNoteOpen,
                            error = error
                        ),
                        edit = EditState(editingId, draftAmount, draftNote),
                        canGoForward = canGoForward,
                        onShiftDate = viewModel::shiftDate,
                        onAmountChange = viewModel::onAmountChange,
                        onCustomNoteChange = viewModel::onNoteChange,
                        onToggleNote = viewModel::toggleNote,
                        onCustomNote = {
                            if (customNoteOpen) viewModel.closeCustomNote() else viewModel.openCustomNote()
                        },
                        onAdd = {
                            // 校验没过就别收键盘 —— 写错了还要再点一次输入框，很烦
                            if (viewModel.addEntry()) keyboard?.hide()
                        },
                        onStartEdit = { expense ->
                            editingId = expense.id
                            draftAmount = MoneyUtil.toPlainInput(expense.amountCents)
                            draftNote = expense.note
                        },
                        onDraftAmountChange = { draftAmount = it },
                        onDraftNoteChange = { draftNote = it },
                        onCommitEdit = { expense ->
                            val cents = MoneyUtil.parseToCents(draftAmount)
                            if (cents != null && cents > 0L) {
                                viewModel.edit(expense, cents, draftNote)
                                editingId = null
                                keyboard?.hide()
                            }
                        },
                        onCancelEdit = { editingId = null },
                        onRemove = viewModel::remove
                    )
                } else {
                    BrowseBody(
                        today = today,
                        totals = totals,
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

/** 「记一笔」的输入区状态。打包成两个小结构，免得函数签名堆二十几个参数 */
private data class InputState(
    val amount: String,
    val note: String,
    val chips: List<String>,
    val selectedNote: String?,
    val customOpen: Boolean,
    val error: String?
)

/** 明细行的编辑态 */
private data class EditState(
    val editingId: Long?,
    val amount: String,
    val note: String
)

/**
 * 「记一笔」：汇总 → 日期导航 → 当天明细 → 底部输入区。
 *
 * 汇总锚定真正的今天，不跟着明细翻页走 —— 见 LedgerViewModel 类注释第 1 条。
 * 输入区固定在下半屏末端，键盘弹出时整条跟着上移（imePadding 在上一层），
 * 所以无论有没有弹键盘，金额和「记」都停在离拇指最近的位置。
 */
@Composable
private fun ColumnScope.RecordBody(
    today: LocalDate,
    viewDate: LocalDate,
    expenses: List<Expense>,
    totals: LedgerTotals,
    input: InputState,
    edit: EditState,
    canGoForward: Boolean,
    onShiftDate: (Long) -> Unit,
    onAmountChange: (String) -> Unit,
    onCustomNoteChange: (String) -> Unit,
    onToggleNote: (String) -> Unit,
    onCustomNote: () -> Unit,
    onAdd: () -> Unit,
    onStartEdit: (Expense) -> Unit,
    onDraftAmountChange: (String) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onCommitEdit: (Expense) -> Unit,
    onCancelEdit: () -> Unit,
    onRemove: (Long) -> Unit
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(14.dp))

        Summary(totals)

        Spacer(Modifier.height(16.dp))

        HairLine()

        Spacer(Modifier.height(6.dp))

        // 日期导航。原来是「← 日期 →」再加一行「记在 X」——
        // 那行永远在、永远说同一件事，等于每屏白送一行高度。
        // 这里把它并进导航：箭头本身已经表达了「正在看哪天」。
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowAction("←", enabled = true) { onShiftDate(-1) }
            Spacer(Modifier.width(4.dp))
            Text(
                text = DateUtil.navLabel(viewDate, today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(4.dp))
            RowAction("→", enabled = canGoForward) { onShiftDate(1) }
        }
    }

    Box(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        if (expenses.isEmpty()) {
            // 空态居中而不是贴左：贴左的一行字看起来像「列表加载到这里为止了」，
            // 居中才像「这一块现在是空的」。
            Text(
                text = "还没记。下面点个标签、输个数就行。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    LedgerRow(
                        expense = expense,
                        editing = edit.editingId == expense.id,
                        draftAmount = edit.amount,
                        draftNote = edit.note,
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
        }
    }

    Spacer(Modifier.height(8.dp))

    NoteChips(
        chips = input.chips,
        selected = input.selectedNote,
        customOpen = input.customOpen,
        onToggle = onToggleNote,
        onCustom = onCustomNote
    )

    Spacer(Modifier.height(2.dp))

    LedgerInputBar(
        amount = input.amount,
        onAmountChange = onAmountChange,
        customNoteOpen = input.customOpen,
        customNote = input.note,
        onCustomNoteChange = onCustomNoteChange,
        onSubmit = onAdd
    )

    input.error?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }

    Spacer(Modifier.height(12.dp))
}

/**
 * 「看花销」：三列统计 → 月份导航 → 月历 → 本月合计。
 *
 * 顶部三个数字原来是挤在一行的小字：「今日 ¥32.00 · 本周 ¥210.00 · 本月 ¥1,204.00」，
 * 一行放不下就截断，而它恰好是这个屏最想说的东西之一。
 * 改成三列之后每个数字都有独立的位置和固定的上限，
 * 「今日」仍用主色压住主次，不至于变成三个等权的仪表盘。
 *
 * 它们锚定的是**此刻**，和下面的月历（正在翻的那个月）是两个时间维度 ——
 * 翻到 3 月时如果这一行也跟着变成 3 月，
 * 那「今天花了多少」就永远看不到了。
 */
@Composable
private fun ColumnScope.BrowseBody(
    today: LocalDate,
    totals: LedgerTotals,
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
        Spacer(Modifier.height(14.dp))

        StatRow(totals)

        Spacer(Modifier.height(16.dp))

        HairLine()

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            RowAction("←", enabled = true) { onShiftMonth(-1) }
            Spacer(Modifier.width(4.dp))
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(4.dp))
            RowAction("→", enabled = canGoNextMonth) { onShiftMonth(1) }
        }

        Spacer(Modifier.height(14.dp))

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

/** 今日 / 本周 / 本月，三列等宽。今日用主色，其余用正文色 */
@Composable
private fun StatRow(totals: LedgerTotals) {
    Row(Modifier.fillMaxWidth()) {
        StatCell("今日", totals.todayCents, accent = true, modifier = Modifier.weight(1f))
        StatCell("本周", totals.weekCents, accent = false, modifier = Modifier.weight(1f))
        StatCell("本月", totals.monthCents, accent = false, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(
    label: String,
    cents: Long,
    accent: Boolean,
    modifier: Modifier
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = MoneyUtil.format(cents),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                lineHeight = 24.sp
            ),
            color = if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1
        )
    }
}

/**
 * 「记一笔 / 看花销」的二级切换，做成分段控件。
 *
 * 上一版是两段带下划线的文字，和顶部「今天 / 规划 / 记账」长得几乎一样，
 * 结果两个层级糊在一起 —— 用户会以为「记一笔」是第四个 Tab。
 * 换成一块两格的实心底板后，它一眼就是「同一屏里的两种视图」。
 */
@Composable
private fun ModeSwitch(mode: LedgerMode, onSelect: (LedgerMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.outline)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Segment("记一笔", mode == LedgerMode.RECORD) { onSelect(LedgerMode.RECORD) }
        Segment("看花销", mode == LedgerMode.BROWSE) { onSelect(LedgerMode.BROWSE) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
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
 * 汇总。
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
 * - 常态：整行可点（进入编辑）。**没有删除** ——
 *   账目删除不可撤销，常驻的「×」紧挨着手指的落点，误触一次就是永久损失。
 *   为了省一次点击而把「误删一笔账」的概率放在每行每屏，不划算。
 * - 编辑态：金额和备注就地变输入框，右侧「✓」保存、「删」删除（错误色）、「×」取消。
 *   删除挪进来之后，它前面多了一道「先点开这一行」的门槛，
 *   而真正想删的时候也就多一次点击。
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
                    .width(56.dp)
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

            Spacer(Modifier.width(4.dp))

            RowAction("✓", enabled = true, onClick = onCommitEdit)
            RowAction(
                label = "删",
                enabled = true,
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
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
                // 没写备注的账不是错账，只是没写。用一条淡横线占位，
                // 比留白好 —— 留白会让整行看起来只有右边半截。
                text = expense.note.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (expense.note.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 9.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = MoneyUtil.format(expense.amountCents),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}
