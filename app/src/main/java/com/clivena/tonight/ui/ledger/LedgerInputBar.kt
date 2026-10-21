package com.clivena.tonight.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 底部输入栏：金额 + 一个「记」。
 *
 * 上一版是两个带框的输入框并排、右下角一个文字按钮 —— 那一排看起来像
 * 一张要填的表，视觉重心散在三处，而且每个框都要单独点一次才能输入。
 * 现在整条是一个整体：金额是这一屏最大的字号，动作收成一枚圆钮，
 * 手指落在这条里的任何位置都知道该干什么。
 *
 * 金额**不加边框**：它是这一屏的主角，主角不该被框住。
 * 备注则完全交给上面的标签条，只有点了「+ 自己写」才展开一个输入框 ——
 * 需要打字是例外，不是常态。
 */
@Composable
fun LedgerInputBar(
    amount: String,
    onAmountChange: (String) -> Unit,
    customNoteOpen: Boolean,
    customNote: String,
    onCustomNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSubmit = amount.isNotBlank()
    val keyboard = LocalSoftwareKeyboardController.current
    val noteFocus = remember { FocusRequester() }

    // 展开就聚焦 —— 既然用户点了「自己写」，他的下一步一定是打字，
    // 让他再点一次输入框是多余的一步。
    LaunchedEffect(customNoteOpen) {
        if (customNoteOpen) {
            noteFocus.requestFocus()
            keyboard?.show()
        }
    }

    Column(modifier.fillMaxWidth()) {
        if (customNoteOpen) {
            BasicTextField(
                value = customNote,
                onValueChange = onCustomNoteChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                decorationBox = { inner ->
                    Box {
                        if (customNote.isEmpty()) {
                            Text(
                                text = "备注，比如「午饭」",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .focusRequester(noteFocus)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )

            Spacer(Modifier.width(6.dp))

            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
                decorationBox = { inner ->
                    Box {
                        if (amount.isEmpty()) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 30.sp,
                                    lineHeight = 38.sp
                                ),
                                // 淡到一眼能看出是占位，不会误认为已经输入了 0
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
            )

            Spacer(Modifier.width(12.dp))

            SubmitButton(enabled = canSubmit, onClick = onSubmit)
        }
    }
}

/**
 * 圆形「记」。
 *
 * 金额为空时变灰且点不动 —— 而不是点了才报错。
 * 按钮的可用状态本身就是「你还差一步」的提示。
 */
@Composable
private fun SubmitButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "记",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    }
}
