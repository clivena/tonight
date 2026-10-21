package com.clivena.tonight.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clivena.tonight.BuildConfig
import com.clivena.tonight.Tab
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.backup.BackupSummary
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.ui.ledger.TagManagerDialog
import com.clivena.tonight.ui.theme.LocalNightMode
import com.clivena.tonight.ui.theme.PaletteId
import com.clivena.tonight.ui.theme.Palettes
import com.clivena.tonight.util.ImageStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 「设置」屏。
 *
 * 只有三件事可调：颜色、背景图、常用标签。
 * 没有「账号」「同步」「隐私协议」「默认页」这一类条目 ——
 * 这个 App 没有账号、不联网、只有一个用户，那些条目在这里没有对应物。
 *
 * 夜间开关不在这 —— 它放在顶栏。它天天要用，而且用的时机是
 * 「现在光线不对」的那一刻，钻到设置里再翻就晚了。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSelectTab: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    val paletteName by viewModel.paletteName.collectAsStateWithLifecycle()
    val backgroundName by viewModel.backgroundName.collectAsStateWithLifecycle()
    val backgroundDim by viewModel.backgroundDim.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val active = PaletteId.fromName(paletteName)
    val nightMode = LocalNightMode.current

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tagManagerOpen by remember { mutableStateOf(false) }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val name = ImageStore.import(
                    context = context,
                    uri = uri,
                    dirName = ImageStore.DIR_THEME,
                    fileName = ImageStore.newBackgroundName()
                )
                if (name != null) {
                    // 新名字已经生效，旧文件就成了孤儿，当场删掉
                    ImageStore.deleteBackground(context, backgroundName)
                    viewModel.setBackground(name)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(48.dp))

            TopTabs(
                active = Tab.SETTINGS,
                onSelect = onSelectTab,
                onSettings = { /* 已经在设置页 */ }
            )

            Spacer(Modifier.height(16.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(28.dp))

                // ── 主题色 ─────────────────────────────────────
                SectionTitle("主题色")
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Palettes.ALL.forEach { palette ->
                        Swatch(
                            // 色点显示的是**当前模式下实际会用到**的那个主色。
                            // 夜间档的主色是提亮过的，如果一直显示浅档的色值，
                            // 用户会选完发现界面颜色和刚点的那个不是一回事。
                            color = if (nightMode) palette.night.accent else palette.light.accent,
                            selected = palette.id == active,
                            onClick = { viewModel.setPalette(palette.id.name) }
                        )
                        Spacer(Modifier.width(14.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = active.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "换色板时四个面一起变。想更暗，点顶栏那个月亮。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))

                // ── 背景图 ─────────────────────────────────────
                SectionTitle("背景图")
                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (backgroundName == null) {
                        "现在用的是纯色底。选一张图会铺在每个 Tab 下面，并压一层遮罩保证字能看清。"
                    } else {
                        "已设置一张图片。下面这根滑杆决定它透出来多少。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    InlineAction(
                        label = if (backgroundName == null) "选择图片" else "换一张",
                        primary = true
                    ) {
                        pickBackground.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }

                    if (backgroundName != null) {
                        InlineAction(label = "移除", primary = false) {
                            ImageStore.deleteBackground(context, backgroundName)
                            viewModel.setBackground(null)
                        }
                    }
                }

                // 没有图的时候不显示滑杆 —— 一个「调了没反应」的控件
                // 比没有这个控件更让人困惑。
                if (backgroundName != null) {
                    Spacer(Modifier.height(20.dp))
                    ClaritySlider(
                        dim = backgroundDim,
                        onChange = viewModel::setBackgroundDim
                    )
                }

                Spacer(Modifier.height(28.dp))

                // ── 常用标签 ────────────────────────────────────
                SectionTitle("常用标签")
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "记账屏底部那一条，共 ${tags.size} 个。删掉标签不会影响已经记过的账。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                InlineAction(label = "管理", primary = true) { tagManagerOpen = true }

                Spacer(Modifier.height(32.dp))

                // ── 数据 ────────────────────────────────────────
                SectionTitle("数据")
                Spacer(Modifier.height(10.dp))

                Text(
                    text = "任务、账目、日记连同配图和主题设置，会打成一个 zip 存到你选的位置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                DataSection(viewModel)

                Spacer(Modifier.height(32.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "今晚 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "数据全在本机，没有账号也不联网。卸载 App 会连账目、任务和日记一起清掉 —— " +
                        "重装或换手机之前，先导一份备份。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(28.dp))
            }
        }

        if (tagManagerOpen) {
            TagManagerDialog(
                tags = tags,
                onDismiss = { tagManagerOpen = false },
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/**
 * 背景清晰度滑杆。
 *
 * 滑杆上「清晰度」往右增大，而底层存的是「遮罩浓度」越大越淡 —— 两者反向。
 * 换算只发生在这里，外面拿到的一直是清晰度 0..1，
 * 交回数据层之前才翻成 alpha。这样调方向的时候只改一处。
 *
 * 不做「日夜两个滑杆」：用户要表达的只有一句「图该清楚到什么程度」，
 * 夜里自动退一档就够了，让同一件事理解两遍没有收益。
 */
@Composable
private fun ClaritySlider(dim: Float, onChange: (Float) -> Unit) {
    val span = SettingsRepository.MAX_BG_DIM - SettingsRepository.MIN_BG_DIM
    // 0 = 最淡，1 = 最清楚
    val clarity = ((SettingsRepository.MAX_BG_DIM - dim) / span).coerceIn(0f, 1f)

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "清晰度",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            // 显示一个定性词而不是百分比：百分比会被读成「清晰度只有 38%」，
            // 而真实含义是「遮罩浓度 88%」——那种数字只会让人想凑个整。
            Text(
                text = when {
                    clarity < 0.34f -> "偏淡"
                    clarity < 0.67f -> "适中"
                    else -> "清楚"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = clarity,
            onValueChange = { v -> onChange(SettingsRepository.MAX_BG_DIM - v * span) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "淡",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "清楚",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "往右拖，背景图更清楚；夜里会自动再淡一点。",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        // 这条是真话，不说会让人白调半天：分辨率是导入那一刻定下的，
        // 滑杆只调遮罩，动不了旧文件的像素。
        Text(
            text = "以前选过的图是按较低分辨率存的，觉得糊就重新选一次。",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                lineHeight = 17.sp
            ),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * 一个色点。
 *
 * 选中的用一圈主色描边，而不是打勾或加粗 ——
 * 描边不占额外宽度，五个色点能一直排成一行，不会因为选中项换位置而跳动。
 */
@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** 行内文字动作。这一屏不需要按钮，几个字就够。不可点时压成灰的，而不是消失 */
@Composable
private fun InlineAction(
    label: String,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            !enabled -> MaterialTheme.colorScheme.outline
            primary -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

/**
 * 「数据」这一段。
 *
 * 两个动作都走系统文件选择器：导出用 `CreateDocument`、恢复用 `OpenDocument`。
 * 这条路线的关键好处是**这个 App 至今没有一条 `uses-permission`**：
 * 能读哪个文件由用户在系统界面里当场指定，既不需要存储权限，也不需要 FileProvider。
 *
 * 恢复的按钮点下去**不会立刻动数据** —— 它只解析出摘要弹出来。
 * 真正的写入发生在弹窗里那一下确认之后，这道关卡是整个功能里最重要的一条。
 */
@Composable
private fun DataSection(viewModel: SettingsViewModel) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        // uri 为 null = 用户在选择器里按了返回，包还在缓存里，得删掉
        if (uri != null) viewModel.finishExport(uri) else viewModel.cancelExport()
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.inspect(uri) }

    val busy = phase != BackupPhase.IDLE

    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        InlineAction(
            label = if (phase == BackupPhase.PACKING) "正在打包…" else "备份到文件",
            primary = true,
            enabled = !busy
        ) {
            // 先把包打好，再让用户选存哪：选择器一关就该有东西可写，
            // 而不是让用户在关掉选择器之后盯着界面等几秒
            viewModel.prepareExport { fileName -> createDocument.launch(fileName) }
        }

        InlineAction(
            label = if (phase == BackupPhase.IMPORTING) "正在导入…" else "从备份恢复",
            primary = false,
            enabled = !busy
        ) {
            // 带上 */* ：下载目录里的 zip 有时被报成 application/octet-stream，
            // 只认 application/zip 会让文件在选择器里变灰、看起来像「找不到备份」
            openDocument.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
    }

    Spacer(Modifier.height(10.dp))

    Text(
        text = if (lastBackupAt == 0L) "还没备份过。" else "上次备份：${stampText(lastBackupAt)}",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
        color = MaterialTheme.colorScheme.outline
    )

    notice?.let { text ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }

    pending?.let { staged ->
        ImportDialog(
            summary = staged.summary,
            onCancel = viewModel::cancelPendingImport,
            onConfirm = viewModel::confirmImport
        )
    }
}

/**
 * 恢复前的摘要。
 *
 * 这一屏存在的理由只有一个：**让用户在不可撤销的动作之前先看清自己要恢复的是什么。**
 * 所以它给的是四个具体的数字，而不是「共 N 项」。
 * 数字对不上（比如日记数是 0 而自己明明写过半年）时，用户会当场取消 ——
 * 这正是它该起的作用。
 */
@Composable
private fun ImportDialog(
    summary: BackupSummary,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("这份备份里有什么") },
        text = {
            Column {
                Text(
                    text = "导出于 ${stampText(summary.exportedAt)}，来自 v${summary.appVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "任务 ${summary.taskCount} · 账目 ${summary.expenseCount} · " +
                        "日记 ${summary.journalCount} · 配图 ${summary.imageCount} 张",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (summary.isEmpty) {
                        "这份备份里没有数据，导入也不会有什么变化。"
                    } else {
                        "导入是合并：手机上已有的内容不会被删，重复的会跳过。" +
                            "两边同一天的日记，留下更新时间较新的那篇。"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !summary.isEmpty) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

/** 备份文件里的时间戳是毫秒，显示成「2026年10月21日 20:07」 */
private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/**
 * 时间缺失时给「未知时间」而不是 1970 年。
 *
 * 备份里的时间戳理论上一定有；但万一读到一份手工改过的、缺这个字段的包，
 * 显示「1970年1月1日 08:00」会让人以为是 App 算错了 ——
 * 说「不知道」比说一个假的具体时间好。
 */
private fun stampText(millis: Long): String =
    if (millis <= 0L) {
        "未知时间"
    } else {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(STAMP)
    }
