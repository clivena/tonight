package com.clivena.tonight.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 配置的唯一出口。
 *
 * ##### 为什么是 SharedPreferences，不是一张表
 *
 * 主题、背景图、常用标签都是**配置**，不是账目数据。
 * 建表就要写迁移，而迁移是本工程唯一可能丢数据的动作 ——
 * 为了存三个字符串去动账本的结构，代价和收益完全不成比例。
 *
 * 放这里的代价是：配置坏了最多丢标签，账本永远完好。
 *
 * ##### 为什么只存字符串
 *
 * [paletteName] 存的是枚举名而不是枚举本身 —— 数据层不该反过来依赖 UI 层的类型。
 * 认不出来（手改过、老版本留下的值）就退回默认，不抛异常。
 */
class SettingsRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("tonight_settings", Context.MODE_PRIVATE)

    private val _paletteName = MutableStateFlow(prefs.getString(KEY_PALETTE, null) ?: "")
    val paletteName: StateFlow<String> = _paletteName.asStateFlow()

    /** 背景图文件名，null = 用默认纯色 */
    private val _backgroundName = MutableStateFlow(prefs.getString(KEY_BACKGROUND, null))
    val backgroundName: StateFlow<String?> = _backgroundName.asStateFlow()

    private val _tags = MutableStateFlow(readTags())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    fun setPalette(name: String) {
        _paletteName.value = name
        prefs.edit().putString(KEY_PALETTE, name).apply()
    }

    fun setBackgroundName(name: String?) {
        _backgroundName.value = name
        prefs.edit().apply {
            if (name == null) remove(KEY_BACKGROUND) else putString(KEY_BACKGROUND, name)
        }.apply()
    }

    /**
     * 加一个标签。@return false 表示没加（空的、太长的、已经存在的、满了）。
     *
     * 「已经存在的」也算失败，但界面不该报错 —— 它本来就在那儿，
     * 用户不需要知道「这次点击没产生新东西」。
     */
    fun addTag(raw: String): Boolean {
        val tag = raw.trim()
        if (tag.isEmpty() || tag.length > MAX_TAG_LENGTH) return false
        if (tag in _tags.value) return false
        if (_tags.value.size >= MAX_TAGS) return false
        update(_tags.value + tag)
        return true
    }

    fun removeTag(tag: String) {
        if (tag !in _tags.value) return
        update(_tags.value - tag)
    }

    /** 恢复成内置的那几个。用户在设置里点「恢复默认」时用 */
    fun resetTags() {
        update(DEFAULT_TAGS)
    }

    private fun update(tags: List<String>) {
        _tags.value = tags
        val array = JSONArray()
        tags.forEach { array.put(it) }
        prefs.edit().putString(KEY_TAGS, array.toString()).apply()
    }

    private fun readTags(): List<String> {
        val raw = prefs.getString(KEY_TAGS, null) ?: return DEFAULT_TAGS
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val tag = array.optString(i, "").trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }.ifEmpty { DEFAULT_TAGS }
        }.getOrElse {
            // 配置被改坏不该让记账屏打不开，退回内置的
            DEFAULT_TAGS
        }
    }

    companion object {
        private const val KEY_PALETTE = "palette"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_TAGS = "note_tags"

        private const val MAX_TAG_LENGTH = 8
        private const val MAX_TAGS = 24

        /**
         * 内置标签：没有「学」的过程，就是固定的这八个。
         *
         * 这是 v0.6 相对 v0.5 的一次方向修正 —— v0.5 从历史账目里统计高频备注，
         * 而用户要的是「我自己决定有哪些」。所以现在标签是用户的资产，
         * 不是算法猜出来的结果。
         */
        val DEFAULT_TAGS = listOf("午饭", "早饭", "晚饭", "咖啡", "地铁", "打车", "超市", "房租")
    }
}
