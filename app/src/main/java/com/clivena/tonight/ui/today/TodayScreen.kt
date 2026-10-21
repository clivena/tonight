package com.clivena.tonight.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.Tab
import com.clivena.tonight.domain.Task
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.util.DateUtil
import java.time.LocalDate

/**
 * 「今天」屏 —— 明暗跟随顶栏那个月亮开关。
 *
 * 一条规则：**清单怎么排就怎么显示**。不做二次排序，勾完也不往下沉。
 * 列表自己会跳的界面，人是记不住自己排的顺序的。
 *
 * v0.6 起主题由顶层统一提供，本屏不再自己包 Theme；
 * 背景也由主题层铺（可能是纯色，也可能是用户选的图），这里只画内容。
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val remaining = tasks.count { !it.isDone }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Spacer(Modifier.height(48.dp))

            TopTabs(
                active = Tab.TODAY,
                onSelect = onSelectTab,
                onSettings = onOpenSettings
            )

            Spacer(Modifier.height(24.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = DateUtil.fullLabel(today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            text = "今天还没有安排。\n去「规划」里写点什么。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(tasks, key = { it.id }) { task ->
                    TodayRow(
                        task = task,
                        onToggle = { viewModel.toggle(task) }
                    )
                }

                if (tasks.isNotEmpty() && remaining == 0) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "今天就这样。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TodayRow(
    task: Task,
    onToggle: () -> Unit
) {
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
                .padding(vertical = 8.dp)
        )
    }
}
