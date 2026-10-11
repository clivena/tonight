package com.clivena.tonight.ui.morning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.ui.theme.TomorrowTheme

/**
 * 明早屏 —— 始终浅色。
 *
 * 这一屏只做一件事：把「开始做」的决策成本降到零。
 * 所以不显示列表、不显示剩余数量、不放任何其他入口。
 *
 * 用户唯一能做的动作是「做完了」，然后下一件自动出现。
 */
@Composable
fun MorningScreen(
    viewModel: MorningViewModel,
    onAllDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    TomorrowTheme {
        val task by viewModel.currentTask.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.allDone.collect { onAllDone() }
        }

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val current = task
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (current != null) {
                    Text(
                        text = current.content,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(48.dp))

                    TextButton(onClick = { viewModel.complete(current.id) }) {
                        Text(
                            text = "做完了",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
