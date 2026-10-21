package com.clivena.tonight.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clivena.tonight.data.SettingsRepository
import com.clivena.tonight.data.backup.BackupService
import com.clivena.tonight.data.backup.BackupFormatException
import com.clivena.tonight.data.backup.ImportResult
import com.clivena.tonight.data.backup.StagedBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 备份/恢复此刻在做什么。界面据此把动作文字换成「正在…」并禁用点击 */
enum class BackupPhase { IDLE, PACKING, INSPECTING, IMPORTING }

/**
 * 设置屏。仍然很薄 —— 配置的状态在 [SettingsRepository] 里，这里只做转发。
 *
 * 备份这一块是个例外：它是**有过程**的操作（打包要几百毫秒到几秒），
 * 所以这里得真的持有几个状态：正在进行什么、上一次的结果是什么、
 * 以及一份「已经解析完、等你点头」的备份。
 *
 * ##### 为什么导出要分成两步（prepare → finish）
 *
 * 系统文件选择器是异步的。要么先把包打好再问用户存哪（本做法），
 * 要么先问用户存哪、拿到 uri 之后才开始查库读图 —— 后者会让选择器
 * 关闭后界面再空转好几秒，那几秒里用户以为卡死了。
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupService
) : ViewModel() {

    val paletteName: StateFlow<String> = settings.paletteName
    val backgroundName: StateFlow<String?> = settings.backgroundName
    val backgroundDim: StateFlow<Float> = settings.backgroundDim
    val tags: StateFlow<List<String>> = settings.tags
    val lastBackupAt: StateFlow<Long> = settings.lastBackupAt

    private val _phase = MutableStateFlow(BackupPhase.IDLE)
    val phase: StateFlow<BackupPhase> = _phase.asStateFlow()

    /** 上一次操作的结果，一句话。下一次动作开始时清掉 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 已经解析好、等用户确认的那份备份。非空时界面弹摘要 */
    private val _pending = MutableStateFlow<StagedBackup?>(null)
    val pending: StateFlow<StagedBackup?> = _pending.asStateFlow()

    /** 还没交出去的那份缓存包。用户在选择器里点了取消也要删掉 */
    private var exportFile: File? = null

    fun setPalette(name: String) = settings.setPalette(name)

    fun setBackground(name: String?) = settings.setBackgroundName(name)

    fun setBackgroundDim(dim: Float) = settings.setBackgroundDim(dim)

    fun addTag(tag: String): Boolean = settings.addTag(tag)

    fun removeTag(tag: String) = settings.removeTag(tag)

    fun resetTags() = settings.resetTags()

    // ── 导出 ────────────────────────────────────────────────

    /**
     * 打包，然后把「存到哪」交给系统选择器。
     *
     * @param onReady 拿到缓存文件和预填文件名后回调，界面在里面 launch 选择器
     */
    fun prepareExport(onReady: (fileName: String) -> Unit) {
        if (_phase.value != BackupPhase.IDLE) return
        viewModelScope.launch {
            _phase.value = BackupPhase.PACKING
            _notice.value = null
            try {
                val file = backup.createPackage()
                exportFile = file
                _phase.value = BackupPhase.IDLE
                onReady(backup.suggestedFileName())
            } catch (e: Exception) {
                _notice.value = "打包失败：${e.readable()}"
                _phase.value = BackupPhase.IDLE
            }
        }
    }

    /** 用户在系统选择器里选好了位置 */
    fun finishExport(uri: Uri) {
        val file = exportFile ?: return
        viewModelScope.launch {
            _phase.value = BackupPhase.PACKING
            try {
                backup.writeTo(uri, file)
                settings.setLastBackupAt(System.currentTimeMillis())
                _notice.value = "已导出 ${readableSize(file.length())}，存到你选的位置了。"
            } catch (e: Exception) {
                _notice.value = "没写出去：${e.readable()}"
            } finally {
                backup.discard(file)
                exportFile = null
                _phase.value = BackupPhase.IDLE
            }
        }
    }

    /** 用户在系统选择器里按了返回 —— 包已经打好但没地方去，删掉 */
    fun cancelExport() {
        backup.discard(exportFile)
        exportFile = null
        _phase.value = BackupPhase.IDLE
    }

    // ── 导入 ────────────────────────────────────────────────

    /**
     * 解析用户选中的文件，弹摘要等确认。
     *
     * 这一步**不写任何东西** —— 它只回答「这份备份里有什么」。
     * 所有写入都在 [confirmImport] 里，而那个方法只有点了按钮才会被调用。
     */
    fun inspect(uri: Uri) {
        if (_phase.value != BackupPhase.IDLE) return
        viewModelScope.launch {
            _phase.value = BackupPhase.INSPECTING
            _notice.value = null
            try {
                _pending.value = backup.stage(uri)
            } catch (e: BackupFormatException) {
                // 这个类型的 message 是写给用户看的，直接显示
                _notice.value = e.message
            } catch (e: Exception) {
                _notice.value = "读不了这个文件：${e.readable()}"
            } finally {
                _phase.value = BackupPhase.IDLE
            }
        }
    }

    fun cancelPendingImport() {
        backup.discard(_pending.value?.file)
        _pending.value = null
    }

    fun confirmImport() {
        val staged = _pending.value ?: return
        if (_phase.value != BackupPhase.IDLE) return
        viewModelScope.launch {
            _phase.value = BackupPhase.IMPORTING
            try {
                _notice.value = describe(backup.apply(staged))
            } catch (e: Exception) {
                _notice.value = "导入没成功：${e.readable()}。已经写进去的部分已回滚。"
            } finally {
                backup.discard(staged.file)
                _pending.value = null
                _phase.value = BackupPhase.IDLE
            }
        }
    }

    // ── 文案 ────────────────────────────────────────────────

    private fun describe(result: ImportResult): String {
        if (result.totalAdded == 0 && result.totalSkipped > 0) {
            return "这份备份里的内容手机上都已有了（跳过 ${result.totalSkipped} 条），没有重复导入。"
        }
        if (result.totalAdded == 0 && result.totalSkipped == 0) {
            return "备份是空的，没有可导入的内容。"
        }

        val parts = mutableListOf<String>()
        if (result.tasksAdded > 0) parts += "任务 ${result.tasksAdded}"
        if (result.expensesAdded > 0) parts += "账目 ${result.expensesAdded}"
        if (result.journalAdded > 0) parts += "日记 ${result.journalAdded}"
        if (result.imagesRestored > 0) parts += "图片 ${result.imagesRestored} 张"

        val builder = StringBuilder("导入完成：")
        builder.append(parts.joinToString("、"))
        if (result.totalSkipped > 0) {
            builder.append("；跳过重复 ${result.totalSkipped} 条")
        }
        if (result.settingsApplied) builder.append("；外观设置已恢复")
        builder.append("。")
        return builder.toString()
    }

    private fun readableSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 ->
            String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    private fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: "未知错误"

    companion object {
        fun factory(
            settings: SettingsRepository,
            backup: BackupService
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(settings, backup) as T
        }
    }
}
