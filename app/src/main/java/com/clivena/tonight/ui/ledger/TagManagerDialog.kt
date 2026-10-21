package com.clivena.tonight.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clivena.tonight.ui.components.RowAction

/**
 * 标签管理。
 *
 * 一年用不了几次，所以不做成常驻页面，而是一个弹层 ——
 * 常驻的配置页会让人以为「这里有什么必须维护的东西」，而它其实什么都不用管。
 *
 * 一句必须写进界面的说明：**删标签不删账**。
 * 账目里存的是备注文本，标签只是往那个文本框里填字的快捷方式；
 * 两者不同步是设计，不是 bug，但用户不知道就会以为删掉了历史。
 */
@Composable
fun TagManagerDialog(
    tags: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.padding(22.dp)) {
                Text(
                    text = "常用标签",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "删掉一个标签不会影响已经记过的账 —— 账里存的是当时写下的字。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(tags, key = { it }) { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            RowAction(
                                label = "删",
                                enabled = true,
                                color = MaterialTheme.colorScheme.error,
                                onClick = { onRemove(tag) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                if (hint != null) hint = null
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (input.isNotBlank() && onAdd(input)) input = ""
                            }),
                            decorationBox = { inner ->
                                Box {
                                    if (input.isEmpty()) {
                                        Text(
                                            text = "新标签，比如「奶茶」",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        text = "加入",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (input.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = input.isNotBlank()) {
                                if (onAdd(input)) {
                                    input = ""
                                    hint = null
                                } else {
                                    hint = "这个名字已经在了"
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                }

                hint?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "完成",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
