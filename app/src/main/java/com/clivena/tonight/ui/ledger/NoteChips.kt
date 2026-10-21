package com.clivena.tonight.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
 * 还没有任何历史备注时先顶上的几个。
 *
 * 不预置的话，第一次打开记账看到的是一条空行，
 * 用户不会知道「这里本来可以点」—— 空状态最糟的地方就是它看起来像坏了。
 *
 * 它们**不入库**：只是界面常量，等真实记录攒起来，
 * 频率统计自然会把它们替换掉。所以也不会污染数据，
 * 更不构成「分类体系」—— 点一下它就只是往备注里写字。
 */
private val FALLBACK_NOTES = listOf("午饭", "早饭", "晚饭", "咖啡", "地铁", "打车", "超市", "房租")

/**
 * 常用备注标签条。
 *
 * 这是「记一笔」提速的核心：记账的真实分布是少数名目反复出现，
 * 把高频的那几个做成一键，用户就不必再为同一笔支出发明第二遍措辞。
 *
 * 两个刻意的取舍：
 * 1. **可以再点一次取消**。选中态必须能表达「这一笔不写备注」，
 *    否则一旦点了标签，就只能靠「自己写」再清空一次。
 * 2. **「+ 自己写」常驻在末尾**，不用长按、不用找设置 ——
 *    兜不住的情况永远存在，出口得看得见。
 */
@Composable
fun NoteChips(
    chips: List<String>,
    selected: String?,
    customOpen: Boolean,
    onToggle: (String) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notes = chips.ifEmpty { FALLBACK_NOTES }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 首尾留出和正文一样的 24dp，滑动时内容从边距内开始、也在边距内结束
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(notes, key = { it }) { note ->
            Chip(
                label = note,
                selected = selected == note,
                onClick = { onToggle(note) }
            )
        }
        item(key = "__custom__") {
            Chip(
                label = if (customOpen) "收起" else "+ 自己写",
                selected = customOpen,
                onClick = onCustom
            )
        }
    }
}

/**
 * 一个标签。选中是实心，未选是描边空心 ——
 * 用填充和描边的差别表示状态，而不是加粗或放大，
 * 这样一排标签的高度和基线永远一致，不会跳。
 */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
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
