package com.clivena.tonight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.ui.common.QuietScreen
import com.clivena.tonight.ui.evening.EveningScreen
import com.clivena.tonight.ui.evening.EveningViewModel
import com.clivena.tonight.ui.morning.MorningScreen
import com.clivena.tonight.ui.morning.MorningViewModel
import com.clivena.tonight.ui.theme.TonightTheme
import com.clivena.tonight.ui.theme.TomorrowTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as TonightApp).repository
        setContent { TonightRoot(repository) }
    }
}

/**
 * 全 App 的路由。
 *
 * 没有引入 Navigation 库 —— 只有四个静态屏幕，一个 when 就够了。
 * 引库的收益是零，成本是几十 KB 加一层抽象。
 */
@Composable
private fun TonightRoot(repository: TaskRepository) {

    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.factory(repository)
    )
    val screen by mainViewModel.screen.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val planDate = mainViewModel.planDate

    when (screen) {

        // 第一帧：还在读数据库判断该去哪，先留白
        null -> Box(Modifier.fillMaxSize())

        Screen.MORNING -> {
            val morningViewModel: MorningViewModel = viewModel(
                key = "morning-$today",
                factory = MorningViewModel.factory(repository, today)
            )
            MorningScreen(
                viewModel = morningViewModel,
                onAllDone = mainViewModel::onAllDone
            )
        }

        Screen.EVENING -> {
            val eveningViewModel: EveningViewModel = viewModel(
                key = "evening-$planDate",
                factory = EveningViewModel.factory(repository, planDate)
            )
            EveningScreen(
                viewModel = eveningViewModel,
                onSaved = mainViewModel::onPlanSaved
            )
        }

        Screen.ALL_DONE -> TomorrowTheme {
            QuietScreen(
                title = "今天就这样。",
                actionLabel = "去规划明天",
                onAction = mainViewModel::onPlan
            )
        }

        Screen.GOOD_NIGHT -> TonightTheme {
            QuietScreen(title = "晚安。")
        }
    }
}
