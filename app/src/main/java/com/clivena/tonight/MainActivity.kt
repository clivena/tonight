package com.clivena.tonight

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.data.JournalRepository
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.ui.journal.JournalScreen
import com.clivena.tonight.ui.journal.JournalViewModel
import com.clivena.tonight.ui.ledger.LedgerScreen
import com.clivena.tonight.ui.ledger.LedgerViewModel
import com.clivena.tonight.ui.plan.PlanScreen
import com.clivena.tonight.ui.plan.PlanViewModel
import com.clivena.tonight.ui.settings.SettingsScreen
import com.clivena.tonight.ui.settings.SettingsViewModel
import com.clivena.tonight.ui.theme.LocalNightMode
import com.clivena.tonight.ui.theme.LocalToggleNightMode
import com.clivena.tonight.ui.theme.PaletteId
import com.clivena.tonight.ui.theme.Palettes
import com.clivena.tonight.ui.theme.TonightTheme
import com.clivena.tonight.ui.today.TodayScreen
import com.clivena.tonight.ui.today.TodayViewModel
import com.clivena.tonight.util.DateUtil
import com.clivena.tonight.util.ImageStore
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TonightApp
        setContent {
            TonightRoot(
                taskRepository = app.repository,
                expenseRepository = app.expenseRepository,
                journalRepository = app.journalRepository,
                settings = app.settingsRepository
            )
        }
    }
}

/**
 * 全 App 的路由 + 主题。
 *
 * 没有引入 Navigation 库 —— 只有五个落点，一个 when 就够了。
 * 引库的收益是零，成本是几十 KB 加一层抽象。
 *
 * ##### 主题为什么在这里
 *
 * v0.6 之前每个 Screen 各自包一层 Theme（「规划」用深色、其余用浅色）。
 * 换色板时那种写法要改四处、还容易漏。现在收到这一层：
 * **色系由用户的选择决定，明暗由那个月亮开关决定，四屏共用一份。**
 *
 * 也正因为收到了这一层，「上夜间模式」只要在这一个地方接一下，
 * 而不用去四个屏里各改一遍。
 */
@Composable
private fun TonightRoot(
    taskRepository: TaskRepository,
    expenseRepository: ExpenseRepository,
    journalRepository: JournalRepository,
    settings: SettingsRepository
) {
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.factory(taskRepository)
    )
    val tab by mainViewModel.tab.collectAsStateWithLifecycle()

    val paletteName by settings.paletteName.collectAsStateWithLifecycle()
    val backgroundName by settings.backgroundName.collectAsStateWithLifecycle()
    val nightMode by settings.nightMode.collectAsStateWithLifecycle()

    val palette = Palettes.of(PaletteId.fromName(paletteName))

    // 状态栏 / 导航栏的图标明暗必须手动托住。
    //
    // enableEdgeToEdge() 默认按**系统**的深色设置决定那排图标是黑是白，
    // 而这里的夜间模式是 App 内的开关，两者并不同步 ——
    // 系统开着浅色、App 开了夜间，那排深色图标就会整个消失在深底上。
    // 这不是细节问题：一个看不见状态栏的夜间模式，是残的。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !nightMode
                isAppearanceLightNavigationBars = !nightMode
            }
        }
    }

    val context = LocalContext.current
    val background by produceState<ImageBitmap?>(null, backgroundName) {
        value = ImageStore.load(context, ImageStore.DIR_THEME, backgroundName)
    }

    // 用 remember 把回调引用钉住：只有开关状态真的变了才产生新 lambda。
    // 不钉的话每帧都是新函数，往下传时会白白触发一轮重组。
    val toggleNight = remember(nightMode) {
        { settings.setNightMode(!nightMode) }
    }

    CompositionLocalProvider(
        LocalNightMode provides nightMode,
        LocalToggleNightMode provides toggleNight
    ) {
        TonightTheme(palette = palette, night = nightMode, background = background) {
            when (tab) {

                // 第一帧：还在读数据库判断该去哪，先留白
                null -> Box(Modifier.fillMaxSize())

                Tab.TODAY -> {
                    val today = remember { LocalDate.now() }
                    val todayViewModel: TodayViewModel = viewModel(
                        key = "today-$today",
                        factory = TodayViewModel.factory(taskRepository, today)
                    )
                    TodayScreen(
                        viewModel = todayViewModel,
                        onSelectTab = mainViewModel::selectTab,
                        onOpenSettings = { mainViewModel.selectTab(Tab.SETTINGS) }
                    )
                }

                Tab.PLAN -> {
                    val planViewModel: PlanViewModel = viewModel(
                        key = "plan",
                        factory = PlanViewModel.factory(taskRepository, DateUtil.defaultPlanDate())
                    )
                    PlanScreen(
                        viewModel = planViewModel,
                        onSelectTab = mainViewModel::selectTab,
                        onOpenSettings = { mainViewModel.selectTab(Tab.SETTINGS) }
                    )
                }

                Tab.LEDGER -> {
                    val ledgerViewModel: LedgerViewModel = viewModel(
                        key = "ledger",
                        factory = LedgerViewModel.factory(expenseRepository, settings)
                    )
                    LedgerScreen(
                        viewModel = ledgerViewModel,
                        onSelectTab = mainViewModel::selectTab,
                        onOpenSettings = { mainViewModel.selectTab(Tab.SETTINGS) }
                    )
                }

                Tab.JOURNAL -> {
                    val journalViewModel: JournalViewModel = viewModel(
                        key = "journal",
                        factory = JournalViewModel.factory(journalRepository)
                    )
                    JournalScreen(
                        viewModel = journalViewModel,
                        onSelectTab = mainViewModel::selectTab,
                        onOpenSettings = { mainViewModel.selectTab(Tab.SETTINGS) }
                    )
                }

                Tab.SETTINGS -> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        key = "settings",
                        factory = SettingsViewModel.factory(settings)
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onSelectTab = mainViewModel::selectTab
                    )
                }
            }
        }
    }
}

/**
 * 从 Compose 拿到的 Context 往上找 Activity。
 *
 * 直接 `as Activity` 在真机上经常能跑，但 LocalView 的 context 常常被包了
 * 一层 ContextThemeWrapper —— 那种情况强转会抛 ClassCastException。
 * 往上剥到 Activity 为止，比赌类型可靠。
 */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
