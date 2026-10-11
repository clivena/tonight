package com.clivena.tonight

import android.app.Application
import com.clivena.tonight.data.LocalTaskRepository
import com.clivena.tonight.data.TaskRepository
import com.clivena.tonight.data.local.AppDatabase

/**
 * 手动持有依赖。
 *
 * 这个规模（1 个 Repository、3 个 ViewModel）不值得引入 Hilt：
 * 依赖链只有一条，手动构造更简单、编译更快、出错面更小。
 * 等 v0.2 加云同步时再考虑。
 */
class TonightApp : Application() {

    val repository: TaskRepository by lazy {
        LocalTaskRepository(AppDatabase.get(this).taskDao())
    }
}
