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
import androidx.compose.ui.unit.sp
import com.clivena.tonight.Tab
import com.clivena.tonight.ui.theme.LocalNightMode
import com.clivena.tonight.ui.theme.LocalToggleNightMode

/**
 * 「今天 / 规划 / 记账 / 日记」+ 右侧「☾ 夜间」+「设置」。
 *
 * 设置**不做成第五个 Tab**：Tab 是内容，设置是配置。
 * 把配置摆在和内容同级的位置，会让顶部导航看起来有五个平等的目的地，
 * 而实际上用户一年点不进设置几次。
 *
 * 夜间开关反过来 —— 它**值得常驻**。因为它会天天用，而且用的时机
 * 恰恰是「现在这一刻光线不对」，那会儿再钻进设置去翻，就来不及了。
 * 它占的位置很小（一个符号），但省掉的是一次三级跳。
 *
 * 顶栏本身渲染在主题里，所以开了夜间模式它也跟着变 ——
 * 不需要另外给它配一套颜色。
 */
@Composable
fun TopTabs(
    active: Tab,
    onSelect: (Tab) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabItem("今天", active == Tab.TODAY) { onSelect(Tab.TODAY) }
        Spacer(Modifier.width(16.dp))
        TabItem("规划", active == Tab.PLAN) { onSelect(Tab.PLAN) }
        Spacer(Modifier.width(16.dp))
        TabItem("记账", active == Tab.LEDGER) { onSelect(Tab.LEDGER) }
        Spacer(Modifier.width(16.dp))
        TabItem("日记", active == Tab.JOURNAL) { onSelect(Tab.JOURNAL) }

        Spacer(Modifier.weight(1f))

        NightToggle()
        Spacer(Modifier.width(10.dp))

        Text(
            text = "设置",
            style = MaterialTheme.typography.bodyMedium,
            color = if (active == Tab.SETTINGS) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSettings)
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

/**
 * 夜间开关。
 *
 * 符号**固定是月亮**，不随状态换成太阳 —— 换成太阳，用户就得先想
 * 「这个太阳是表示现在，还是表示点一下会变成什么」，而这题没有标准答案。
 * 一个符号 + 两种亮度（灰 = 关，主色 + 淡底 = 开），没有理解成本。
 *
 * 用文字符号而不是图标库，和 [RowAction] 是同一个理由：
 * 省一个依赖，而且符号本来就是最适合这里的东西。
 */
@Composable
private fun NightToggle() {
    val night = LocalNightMode.current
    val toggle = LocalToggleNightMode.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (night) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = toggle)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = "☾",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = if (night) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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
