package com.clivena.tonight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 清单行右侧的小动作（↑ ↓ × ✓）。
 *
 * 刻意不用图标库：三个符号就够用，省下一个依赖和一堆矢量资源。
 * 同时因为它是 Text 而不是 IconButton，尺寸小、不抢注意力 ——
 * 一行里的次要动作不该长得比内容还显眼。
 */
@Composable
fun RowAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.outline
        },
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp)
    )
}
