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
import com.clivena.tonight.data.ExpenseRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.ui.ledger.LedgerScreen
import com.clivena.tonight.ui.ledger.LedgerViewModel
import com.clivena.tonight.ui.plan.PlanScreen
import com.clivena.tonight.ui.plan.PlanViewModel
import com.clivena.tonight.ui.today.TodayScreen
import com.clivena.tonight.ui.today.TodayViewModel
import com.clivena.tonight.util.DateUtil
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TonightApp
        setContent { TonightRoot(app.repository, app.expenseRepository) }
    }
}

/**
 * 全 App 的路由。
 *
 * 没有引入 Navigation 库 —— 只有三个 Tab，一个 when 就够了。
 * 引库的收益是零，成本是几十 KB 加一层抽象。
 */
@Composable
private fun TonightRoot(
    repository: TaskRepository,
    expenseRepository: ExpenseRepository
) {

    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.factory(repository)
    )
    val tab by mainViewModel.tab.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }

    when (tab) {

        // 第一帧：还在读数据库判断该去哪，先留白
        null -> Box(Modifier.fillMaxSize())

        Tab.TODAY -> {
            val todayViewModel: TodayViewModel = viewModel(
                key = "today-$today",
                factory = TodayViewModel.factory(repository, today)
            )
            TodayScreen(
                viewModel = todayViewModel,
                onSelectTab = mainViewModel::selectTab
            )
        }

        Tab.PLAN -> {
            val planViewModel: PlanViewModel = viewModel(
                key = "plan",
                factory = PlanViewModel.factory(repository, DateUtil.defaultPlanDate())
            )
            PlanScreen(
                viewModel = planViewModel,
                onSelectTab = mainViewModel::selectTab
            )
        }

        Tab.LEDGER -> {
            val ledgerViewModel: LedgerViewModel = viewModel(
                key = "ledger",
                factory = LedgerViewModel.factory(expenseRepository)
            )
            LedgerScreen(
                viewModel = ledgerViewModel,
                onSelectTab = mainViewModel::selectTab
            )
        }
    }
}
