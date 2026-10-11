package com.clivena.tonight.ui.evening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.ui.theme.TonightTheme

/**
 * 今晚屏 —— 始终深色。
 *
 * 设计要点：
 * · 没有红点、没有角标、没有进度、没有动效 —— 睡前不该有任何东西催你
 * · 上限到了用一句人话拦住，不用「已达上限」这种系统口吻
 * · 只有真放进了东西，「就这样」才出现
 */
@Composable
fun EveningScreen(
    viewModel: EveningViewModel,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    TonightTheme {
        val tasks by viewModel.tasks.collectAsStateWithLifecycle()
        val input by viewModel.input.collectAsStateWithLifecycle()
        val limitReached by viewModel.limitReached.collectAsStateWithLifecycle()

        val keyboard = LocalSoftwareKeyboardController.current

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(64.dp))

                Text(
                    text = "明天",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "想到什么写什么，最多 ${EveningViewModel.MAX_TASKS} 件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                tasks.forEach { task ->
                    TaskRow(
                        content = task.content,
                        onRemove = { viewModel.removeTask(task.id) }
                    )
                }

                if (limitReached) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "够了。真的够了。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (tasks.size < EveningViewModel.MAX_TASKS) {
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

                    Spacer(Modifier.height(4.dp))

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

                Spacer(Modifier.weight(1f))

                if (tasks.isNotEmpty()) {
                    TextButton(
                        onClick = onSaved,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "就这样",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun TaskRow(
    content: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onRemove) {
            Text(
                text = "去掉",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
