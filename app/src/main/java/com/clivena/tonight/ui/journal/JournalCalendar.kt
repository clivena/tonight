package com.clivena.tonight.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clivena.tonight.util.DateUtil
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日记月历：哪几天写过，一眼看完。
 *
 * 这一块只负责铺格子，月份导航留在 [JournalScreen] 那一行 ——
 * 组件的职责到「画一个月」为止，不掺翻页逻辑。
 *
 * 三个和记账月历（[com.clivena.tonight.ui.ledger.MonthGrid]）**故意不一样**的地方：
 *
 * 1. **不用底色深浅，只用一个小圆点**。记账那格深浅表示「这天花了多少」，
 *    日记没有可比的量 —— 写三行和写三屏都是「写了」。用深浅会让人把
 *    「写了一行」读成废稿，而它可能正是那天唯一记得住的一件事。
 * 2. **格子不显示第二个数字**。日子本身就够读了，底部一句「这个月写了 N 天」
 *    比 30 个小数字更有用。
 * 3. **字色随「有没有写过」分两档**，和圆点重复表达同一件事 ——
 *    这是有意的：纯背景图档（遮罩拖到 0）时圆点可能被图吃掉，
 *    字色的差别不会。两层编码，掉一层还剩一层。
 *
 * 字色一律取主题的 `onBackground` / `onSurfaceVariant`，一个色值都不写死，
 * 夜间模式才能自己反过来。
 */
@Composable
fun JournalCalendar(
    month: YearMonth,
    writtenDates: Set<LocalDate>,
    today: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val writtenThisMonth = writtenDates.count { YearMonth.from(it) == month }

    Column(modifier.fillMaxWidth()) {

        Row(Modifier.fillMaxWidth()) {
            WEEK_HEADER.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 恒 35 或 42 格（见 DateUtil.monthGridDays）—— 翻月时下面的统计行
        // 不会跟着上下跳，这是「五行的月份」也要补满的原因
        DateUtil.monthGridDays(month).chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            written = date in writtenDates,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            onClick = { onPickDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (writtenThisMonth == 0) {
                "这个月还没写"
            } else {
                "这个月写了 $writtenThisMonth 天"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一格。天号 + 一个圆点。
 *
 * 圆点用一个固定 4dp 的占位 Box，没写过就填透明 ——
 * 不能让「没圆点」的格子少一行，否则整月的天号一高一低，看着像排歪了。
 */
@Composable
private fun DayCell(
    date: LocalDate,
    written: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .height(CELL_HEIGHT)
            .clip(shape)
            .then(
                // 「今天」用正文色描边，不用主色：万一这天也有主色圆点，
                // 主色描边会和圆点糊在一起
                if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = !isFuture, onClick = onClick)
            .alpha(if (isFuture) 0.3f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 14.sp
            ),
            color = if (written) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(3.dp))

        Box(
            Modifier
                .size(DOT_SIZE)
                .clip(CircleShape)
                .background(
                    if (written) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}

private val WEEK_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

private val CELL_HEIGHT = 44.dp

private val DOT_SIZE = 4.dp
