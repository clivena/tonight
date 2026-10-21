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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.clivena.tonight.ui.components.TopTabs
import com.clivena.tonight.ui.ledger.TagManagerDialog
import com.clivena.tonight.ui.theme.LocalNightMode
import com.clivena.tonight.ui.theme.PaletteId
import com.clivena.tonight.ui.theme.Palettes
import com.clivena.tonight.util.ImageStore
import kotlinx.coroutines.launch

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
                        "已设置一张图片。夜间模式下遮罩会加厚，图还在，但只剩一层淡纹理。"
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
                    text = "数据全在本机，没有账号也不联网。卸载 App 会连账目、任务和日记一起清掉。",
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

/** 行内文字动作。这一屏不需要按钮，几个字就够 */
@Composable
private fun InlineAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (primary) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    )
}
