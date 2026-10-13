package com.clivena.tonight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clivena.tonight.Tab

/**
 * 「今天 / 规划 / 记账」切换栏。
 *
 * 它渲染在各自屏幕的主题里 —— 所以切到「规划」时，
 * 整条栏连同背景一起由浅转深。这个明暗跳变是有意的：
 * 白天和夜里本来就该是两种气质，三栏里只有「规划」是夜里那一面。
 */
@Composable
fun TopTabs(
    active: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabItem("今天", active == Tab.TODAY) { onSelect(Tab.TODAY) }
        Spacer(Modifier.width(20.dp))
        TabItem("规划", active == Tab.PLAN) { onSelect(Tab.PLAN) }
        Spacer(Modifier.width(20.dp))
        TabItem("记账", active == Tab.LEDGER) { onSelect(Tab.LEDGER) }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(18.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}
