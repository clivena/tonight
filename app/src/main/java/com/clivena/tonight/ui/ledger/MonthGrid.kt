package com.clivena.tonight.ui.ledger

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clivena.tonight.util.DateUtil
import com.clivena.tonight.util.MoneyUtil
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历：一个月铺成一屏的格子。
 *
 * 每一格是「一天 + 那天花了多少」，底色深浅表示这天相对本月算多算少。
 * 格子里那个数字本身就是最好的说明 —— 所以不用再加图例解释颜色。
 *
 * 三个刻意的决定：
 *
 * 1. **不是滚动的**。一个月最多 6 行，直接排就行。用 LazyVerticalGrid
 *    会和外层的滚动容器打架，而这个尺寸本来也不值得上懒加载。
 * 2. **第一列是周一**，和「本周」用同一套周界。
 * 3. **文字一律用深色**，深浅只做在底色上。若改成「底色深就用白字」，
 *    白字要够对比度就得把底色压到几乎全蓝，档位之间的差别反而没了 ——
 *    而档位分不开，这个月历就白做了。现在 4 档底色下深字的对比度都在 5.5:1 以上。
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    dailyTotals: Map<LocalDate, Long>,
    peakDayCents: Long,
    today: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
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

        DateUtil.monthGridDays(month).chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    if (date == null) {
                        // 不属于这个月的格子。留空而不是画灰块 ——
                        // 月历只需要说清「这 30 天」，不需要把上个月尾巴也画出来。
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            amountCents = dailyTotals[date],
                            level = levelOf(dailyTotals[date], peakDayCents),
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
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    amountCents: Long?,
    level: Int,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = LEVEL_ALPHA[level]))
            .then(
                // 「今天」用一圈深色描边标出来。
                // 不用主色描边：如果这天花得最多，底色本身就是主色，描边会消失在里面。
                // 深色在全部 4 档底色上都够清楚。
                if (isToday) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.onBackground, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = !isFuture, onClick = onClick)
            .alpha(if (isFuture) 0.3f else 1f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp
                ),
                color = if (level == 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )

            if (amountCents != null && amountCents > 0L) {
                Text(
                    text = MoneyUtil.formatCompact(amountCents),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 这天相对本月峰值落在第几档（0 = 没花销）。
 *
 * 分档而不是连续插值，是为了让颜色始终能读：
 * 连续 alpha 在浅色端几乎看不出差别，在深色端又会把文字压到看不清。
 */
private fun levelOf(amountCents: Long?, peakDayCents: Long): Int {
    if (amountCents == null || amountCents <= 0L || peakDayCents <= 0L) return 0
    val ratio = amountCents.toDouble() / peakDayCents.toDouble()
    return when {
        ratio <= 0.25 -> 1
        ratio <= 0.50 -> 2
        ratio <= 0.75 -> 3
        else -> 4
    }
}

private val WEEK_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

private val CELL_HEIGHT = 42.dp

/** 5 档不透明度：0 档直接透明，往后每档加深 */
private val LEVEL_ALPHA = floatArrayOf(0f, 0.08f, 0.20f, 0.36f, 0.55f)
