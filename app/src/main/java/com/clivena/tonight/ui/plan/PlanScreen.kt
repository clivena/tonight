package com.clivena.tonight.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.Tab
import com.clivena.tonight.domain.Task
import com.clivena.tonight.ui.components.RowAction
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.util.DateUtil
import java.time.LocalDate

/**
 * 「规划」屏。
 *
 * 这一屏主要在睡前用，所以不给红点、不给角标、不给进度条 ——
 * 睡前不该有任何东西催你。
 *
 * 但它不再单独用深色。以前那一屏是暗的（睡前低刺激），
 * 代价是四个 Tab 里只有它会整屏翻黑，切过去像换了另一个 App。
 * 现在四屏同底色，深浅交给系统的夜间亮度去管。
 *
 * 「安静」不等于「不能动」：清单每一条都可以改文字、挪位置、删掉。
 */
@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val planDate by viewModel.planDate.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 哪一条正在被编辑，以及编辑中的草稿
    var editingId by remember { mutableStateOf<Long?>(null) }
    var draft by remember { mutableStateOf("") }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Spacer(Modifier.height(48.dp))

            TopTabs(
                active = Tab.PLAN,
                onSelect = onSelectTab,
                onSettings = onOpenSettings
            )

            Spacer(Modifier.height(24.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = DateUtil.labelFor(planDate, today),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateChip("今天", planDate == today) { viewModel.selectDate(today) }
                    Spacer(Modifier.width(8.dp))
                    DateChip("明天", planDate == today.plusDays(1)) {
                        viewModel.selectDate(today.plusDays(1))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            text = "还没有。想到什么写什么。\n第二天打开「今天」，照着做就行。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                itemsIndexed(
                    items = tasks,
                    key = { _, task -> task.id }
                ) { index, task ->
                    PlanRow(
                        task = task,
                        editing = editingId == task.id,
                        draft = draft,
                        canMoveUp = index > 0,
                        canMoveDown = index < tasks.lastIndex,
                        onDraftChange = { draft = it },
                        onStartEdit = {
                            editingId = task.id
                            draft = task.content
                        },
                        onCommitEdit = {
                            viewModel.rename(task.id, draft)
                            editingId = null
                            keyboard?.hide()
                        },
                        onToggle = { viewModel.toggle(task) },
                        onMoveUp = { viewModel.moveUp(task.id) },
                        onMoveDown = { viewModel.moveDown(task.id) },
                        onDelete = {
                            if (editingId == task.id) editingId = null
                            viewModel.remove(task.id)
                        }
                    )
                }
            }

            Column(Modifier.padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "比如：把周报写完",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.addTask()
                            keyboard?.hide()
                        }
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            viewModel.addTask()
                            keyboard?.hide()
                        },
                        enabled = input.isNotBlank()
                    ) {
                        Text("加进去", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanRow(
    task: Task,
    editing: Boolean,
    draft: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDraftChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    // 进入编辑态时自动抢焦点；失焦（点了别处）即提交。
    // everFocused 用来挡掉「刚组合出来还没聚焦」那一下的假失焦。
    val focusRequester = remember { FocusRequester() }
    var everFocused by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            everFocused = false
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { onToggle() }
        )

        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            everFocused = true
                        } else if (everFocused) {
                            onCommitEdit()
                        }
                    }
                    .padding(vertical = 8.dp)
            )
        } else {
            Text(
                text = task.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isDone) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onStartEdit)
                    .padding(vertical = 8.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        RowAction("↑", enabled = canMoveUp, onClick = onMoveUp)
        RowAction("↓", enabled = canMoveDown, onClick = onMoveDown)
        RowAction("×", enabled = true, onClick = onDelete)
    }
}

@Composable
private fun DateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}
