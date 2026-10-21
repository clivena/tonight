package com.clivena.tonight.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clivena.tonight.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置屏。薄得几乎不像一个 ViewModel —— 因为配置本身的状态
 * 就在 [SettingsRepository] 里，这里只做转发。
 *
 * 这样写的好处是：改主题色不需要等一次「保存」，四个屏同时跟着变；
 * 代价是没有「确定 / 取消」，点错了就会立刻生效。
 * 对一个只有五套配色的个人 App 来说，即时生效比撤销更重要。
 */
class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {

    val paletteName: StateFlow<String> = settings.paletteName
    val backgroundName: StateFlow<String?> = settings.backgroundName
    val backgroundDim: StateFlow<Float> = settings.backgroundDim
    val tags: StateFlow<List<String>> = settings.tags

    fun setPalette(name: String) = settings.setPalette(name)

    fun setBackground(name: String?) = settings.setBackgroundName(name)

    fun setBackgroundDim(dim: Float) = settings.setBackgroundDim(dim)

    fun addTag(tag: String): Boolean = settings.addTag(tag)

    fun removeTag(tag: String) = settings.removeTag(tag)

    fun resetTags() = settings.resetTags()

    companion object {
        fun factory(settings: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(settings) as T
            }
    }
}
