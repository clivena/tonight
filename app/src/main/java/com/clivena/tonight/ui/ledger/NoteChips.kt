package com.clivena.tonight.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 常用备注标签条。
 *
 * v0.6 起标签是**用户的资产**，不是算法统计出来的：
 * 一开始是内置的八个，之后自己加的会一直留着，长按可以进来删。
 *
 * 两个刻意的取舍：
 * 1. **可以再点一次取消**。选中态必须能表达「这一笔不写备注」，
 *    否则一旦点了标签，就只能靠「自己写」再清空一次。
 * 2. **「+ 自己写」常驻在末尾**，不用长按、不用找设置 ——
 *    兜不住的情况永远存在，出口得看得见。
 *
 * 长按任意标签 → 打开管理面板。管理入口没有单独做成一个按钮，
 * 是因为它一年用不了几次，不该常驻占位置。
 */
@Composable
fun NoteChips(
    chips: List<String>,
    selected: String?,
    customOpen: Boolean,
    onToggle: (String) -> Unit,
    onCustom: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 首尾留出和正文一样的 24dp，滑动时内容从边距内开始、也在边距内结束
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(chips, key = { it }) { note ->
            Chip(
                label = note,
                selected = selected == note,
                onClick = { onToggle(note) },
                onLongClick = onManage
            )
        }
        item(key = "__custom__") {
            Chip(
                label = if (customOpen) "收起" else "+ 自己写",
                selected = customOpen,
                onClick = onCustom,
                onLongClick = null
            )
        }
    }
}

/**
 * 一个标签。选中是实心，未选是描边空心 ——
 * 用填充和描边的差别表示状态，而不是加粗或放大，
 * 这样一排标签的高度和基线永远一致，不会跳。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}
