package com.clivena.tonight.ui.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.Tab
import com.clivena.tonight.ui.components.RowAction
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.util.DateUtil
import com.clivena.tonight.util.ImageStore
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 心情的五个档位。用文字而不是符号：符号要学，文字不用 */
private val MOOD_LABELS = listOf("很差", "一般", "还行", "不错", "很好")

/**
 * 「日记」屏 —— 明暗跟随顶栏那个月亮开关。
 *
 * 这一屏刻意什么都不多给：没有分类、没有标签、没有搜索、没有「新建」按钮。
 * 打开就是今天那一页，写就完了。想翻旧的按「←」，或者点「日历」看哪几天写过。
 *
 * 日历是**这一屏的第二种看法**，不是第五个 Tab：它只有一个用途 ——
 * 一天一天点箭头找上周那篇要按七次，找上个月的要按三十次。
 * 日历给的是「哪些天有东西」的全局视野，点一下就到。
 *
 * 输入框不限高度、不限字数、不停提示「还剩多少字」——
 * 日记唯一的门槛是「要不要开始写」，任何附加的界面元素都在抬高这个门槛。
 * 所以日历藏在右上角一个文字按钮里，不进顶栏、不占 Tab。
 */
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onSelectTab: (Tab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val viewDate by viewModel.viewDate.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()
    val imageName by viewModel.imageName.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val calendarOpen by viewModel.calendarOpen.collectAsStateWithLifecycle()
    val viewMonth by viewModel.viewMonth.collectAsStateWithLifecycle()
    val writtenDates by viewModel.writtenDates.collectAsStateWithLifecycle()
    val canGoNextMonth by viewModel.canGoNextMonth.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 离开这一屏时把没落盘的内容补上（切 Tab 不会触发 ViewModel 销毁）
    DisposableEffect(Unit) {
        onDispose { viewModel.flush() }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val name = ImageStore.import(
                    context = context,
                    uri = uri,
                    dirName = ImageStore.DIR_JOURNAL,
                    fileName = "journal_${viewDate}_${System.currentTimeMillis()}.jpg"
                )
                if (name != null) {
                    // 换图就把旧文件删掉，否则私有目录会随着换图次数不断长胖
                    ImageStore.delete(context, ImageStore.DIR_JOURNAL, imageName)
                    viewModel.setImage(name)
                }
            }
        }
    }

    // 配图按 1080 采样就够：屏幕上显示宽度不到 400dp，再高清只是白占内存
    val bitmap by produceState<ImageBitmap?>(null, imageName) {
        value = ImageStore.load(context, ImageStore.DIR_JOURNAL, imageName, maxDimen = 1080)
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Spacer(Modifier.height(48.dp))

            TopTabs(
                active = Tab.JOURNAL,
                onSelect = onSelectTab,
                onSettings = onOpenSettings
            )

            Spacer(Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "日记",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (calendarOpen) {
                        // 日历模式下这一行换成月份导航。左右箭头仍然是「← →」，
                        // 但作用单位变成月 —— 靠旁边的「2026年11月」说清这一点
                        RowAction("←", enabled = true) { viewModel.shiftMonth(-1) }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = DateUtil.monthLabel(viewMonth),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(4.dp))
                        RowAction("→", enabled = canGoNextMonth) { viewModel.shiftMonth(1) }

                        Spacer(Modifier.weight(1f))

                        // 翻到三个月前想回来，不用点三次箭头
                        RowAction(
                            label = "今天",
                            enabled = true,
                            color = MaterialTheme.colorScheme.primary
                        ) { viewModel.openDate(today) }

                        RowAction("完成", enabled = true) { viewModel.closeCalendar() }
                    } else {
                        RowAction("←", enabled = true) { viewModel.shiftDate(-1) }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = DateUtil.navLabel(viewDate, today),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(4.dp))
                        RowAction("→", enabled = canGoForward) { viewModel.shiftDate(1) }

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = if (saved) "已保存" else "编辑中…",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.width(6.dp))

                        // 日历是「往回找」的入口，不是这一屏的主动作 ——
                        // 所以它放在最右、用主色文字，但不占一整行
                        RowAction(
                            label = "日历",
                            enabled = true,
                            color = MaterialTheme.colorScheme.primary
                        ) { viewModel.openCalendar() }
                    }
                }
            }

            if (calendarOpen) {
                Spacer(Modifier.height(14.dp))

                JournalCalendar(
                    month = viewMonth,
                    writtenDates = writtenDates,
                    today = today,
                    onPickDate = viewModel::openDate,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // 把月历顶在上半屏：下面空着就是空着，不为了「填满」硬塞东西进去
                Spacer(Modifier.weight(1f))
            } else {
                JournalEditor(
                    content = content,
                    onContentChange = viewModel::onContentChange,
                    mood = mood,
                    onPickMood = viewModel::setMood,
                    imageName = imageName,
                    bitmap = bitmap,
                    onPickImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onRemoveImage = {
                        ImageStore.delete(context, ImageStore.DIR_JOURNAL, imageName)
                        viewModel.setImage(null)
                    }
                )
            }
        }
    }
}

/**
 * 编辑区：正文 + 配图 + 心情。
 *
 * 从 [JournalScreen] 里抽出来，是因为加了日历以后那一屏有两个完全不同的形态，
 * 挤在一个函数里会让「这一屏到底有几种看法」变得难读。
 * 抽出来之后 [JournalScreen] 只负责「现在给哪一种」。
 */
@Composable
private fun ColumnScope.JournalEditor(
    content: String,
    onContentChange: (String) -> Unit,
    mood: Int?,
    onPickMood: (Int) -> Unit,
    imageName: String?,
    bitmap: ImageBitmap?,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Spacer(Modifier.height(14.dp))

    BasicTextField(
        value = content,
        onValueChange = onContentChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 27.sp,
            color = MaterialTheme.colorScheme.onBackground
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (content.isEmpty()) {
                    Text(
                        text = "今天想记点什么？\n不写也行，字和图总得有一个。",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 27.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )

    Spacer(Modifier.height(10.dp))

    if (imageName != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(170.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outline)
                )
            }

            Text(
                text = "×",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable(onClick = onRemoveImage)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "心情",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(12.dp))

        MoodDots(mood = mood, onPick = onPickMood)

        Spacer(Modifier.width(12.dp))

        Text(
            text = mood?.let { MOOD_LABELS[it - 1] } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        if (imageName == null) {
            Text(
                text = "+ 配图",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onPickImage)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
    }

    Spacer(Modifier.height(18.dp))
}

/**
 * 五个点，选中填实。
 *
 * 不用「很差 / 一般 / …」五个文字按钮：一行放不下五个词，缩小字号又会
 * 让这一行比正文还抢眼。点 + 旁边一个当前档位的文字，信息一样，噪音小得多。
 */
@Composable
private fun MoodDots(mood: Int?, onPick: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 1..5) {
            val on = mood == index
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .border(
                        width = 1.dp,
                        color = if (on) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clickable { onPick(index) }
            )
        }
    }
}
