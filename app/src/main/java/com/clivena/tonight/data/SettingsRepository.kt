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

    /**
     * 背景图的遮罩浓度：**数值越大，图越淡**（0.88 就是 v0.7 那个默认）。
     *
     * 存的是最终生效的 alpha，不是界面上那个「清晰度百分比」——
     * 真正起作用的就是它，翻 prefs 一眼能看到几，不用反推。
     * 滑杆往哪边拖是展示层的换算，放在设置屏里。
     *
     * 读的时候 clamp 一次：手改过 prefs、或者以后调整了范围，
     * 都不该让底色变成全透明或纯色。
     */
    private val _backgroundDim = MutableStateFlow(
        prefs.getFloat(KEY_BG_DIM, DEFAULT_BG_DIM).coerceIn(MIN_BG_DIM, MAX_BG_DIM)
    )
    val backgroundDim: StateFlow<Float> = _backgroundDim.asStateFlow()

    /**
     * 夜间模式。
     *
     * 默认关 —— 这个 App 的主场景是白天勾清单，夜间是少数时候。
     * 不做「跟随系统」：系统的深色设置管的是整个手机，而这一个开关管的是
     * 四个 Tab 的底色，两者口径不同。跟随系统会让「我明明关了它却变黑了」
     * 这种事发生，而用户找不到原因。
     */
    private val _nightMode = MutableStateFlow(prefs.getBoolean(KEY_NIGHT, false))
    val nightMode: StateFlow<Boolean> = _nightMode.asStateFlow()

    private val _tags = MutableStateFlow(readTags())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    /**
     * 上次导出成功的时间戳，0 = 从没导出过。
     *
     * 存这个是为了让设置页能说一句「上次备份：10 月 21 日」。
     * 备份这种事的失效方式不是「不好用」，是「忘了」——
     * 没有任何提示的话，第一次备份常常发生在某次后悔之后。
     */
    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L))
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    fun setPalette(name: String) {
        _paletteName.value = name
        prefs.edit().putString(KEY_PALETTE, name).apply()
    }

    fun setNightMode(on: Boolean) {
        _nightMode.value = on
        prefs.edit().putBoolean(KEY_NIGHT, on).apply()
    }

    fun setBackgroundName(name: String?) {
        _backgroundName.value = name
        prefs.edit().apply {
            if (name == null) remove(KEY_BACKGROUND) else putString(KEY_BACKGROUND, name)
        }.apply()
    }

    fun setLastBackupAt(millis: Long) {
        _lastBackupAt.value = millis
        prefs.edit().putLong(KEY_LAST_BACKUP, millis).apply()
    }

    /**
     * 从备份里恢复配置。
     *
     * 逐项走的是和手动设置完全相同的入口（[setPalette] / [setNightMode] …），
     * 不直接写 prefs —— 那样的话「恢复的值越界了怎么办」就要在第二个地方再答一遍，
     * 而答案迟早会不一致。
     */
    fun applySnapshot(
        palette: String,
        night: Boolean,
        dim: Float,
        background: String?,
        tags: List<String>
    ) {
        // 空的设计不是「用户选了空的」，是这份备份里那个字段本来就没值 —— 不动
        if (palette.isNotBlank()) setPalette(palette)
        setNightMode(night)
        setBackgroundDim(dim)
        setBackgroundName(background)
        setTags(tags)
    }

    /**
     * 整体替换标签。
     *
     * 和 [addTag] 的差别：这里不做「已存在就失败」的判断，因为来源是备份，
     * 重复项该静默去掉而不是报错；空列表直接忽略 ——
     * 一个标签都没有的记账屏，标签条就成了一片空白。
     */
    private fun setTags(raw: List<String>) {
        val cleaned = raw
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
            .distinct()
            .take(MAX_TAGS)
        if (cleaned.isEmpty()) return
        update(cleaned)
    }

    /**
     * 调背景清晰度。
     *
     * 拖动时每帧都会调到这里，所以是 apply() 而不是 commit() ——
     * apply 只把值写进内存并排一次异步落盘，不会卡住拖手感。
     * 拖到一半杀进程也不会丢，因为最后一次 apply 已经排进队列。
     */
    fun setBackgroundDim(dim: Float) {
        val value = dim.coerceIn(MIN_BG_DIM, MAX_BG_DIM)
        _backgroundDim.value = value
        prefs.edit().putFloat(KEY_BG_DIM, value).apply()
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
        private const val KEY_BG_DIM = "bg_dim"
        private const val KEY_NIGHT = "night_mode"
        private const val KEY_TAGS = "note_tags"
        private const val KEY_LAST_BACKUP = "last_backup_at"

        /**
         * 最清楚的一端：**0，也就是完全不压** —— 屏幕上剩下的只有那张图。
         *
         * v0.8 这里写的是 0.75，理由是「再薄正文会发灰」。那个理由本身没错，
         * 错在把「可读性」和「遮罩」绑成了一件事：遮罩是唯一手段，所以只能留 0.75。
         * v0.10 把两者拆开 —— 遮罩归零，可读性交给文字自己的底（见 Theme 的内容面板）。
         * 手段不再唯一，「滑杆到底还是有一层膜」这个限制也就没必要存在了。
         */
        const val MIN_BG_DIM = 0f

        /** 最淡的一端：图只剩 4%，基本等于纯色底 */
        const val MAX_BG_DIM = 0.96f

        /** 默认对齐 v0.7 的那个值 —— 装完不动的话，看起来和上一版一模一样 */
        const val DEFAULT_BG_DIM = 0.88f

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
