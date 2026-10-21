package com.clivena.tonight.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 备份格式的往返测试。
 *
 * 这个工程至今没有模拟器可用，所以「装到手机上点一下」这条路走不通。
 * 而备份的写入与读取恰好**完全不依赖 Android**（只用 `java.util.zip` 和 `org.json`），
 * 于是它成了这个 App 里少数几件能在编译阶段就自证的事：
 *
 *   写一份 → 读回来 → 每个字段都和写进去的一样。
 *
 * 这几条断言挡住的都是「编译能过、上手机才发现」的错：
 * 键名写错一个字母（每次恢复少一列）、CSV 少了 BOM（Excel 全是乱码）、
 * 图片条目路径不对（日记配图恢复完全部空白）。
 */
class BackupRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    /**
     * 刻意塞了几种「会把格式搞坏」的内容：
     * ASCII 逗号（CSV 分隔符）、换行、中文引号、可空字段为 null。
     */
    private val sample = BackupPayload(
        appVersion = "0.9.0",
        exportedAt = 1_761_100_000_000L,
        settings = SettingsSnapshot(
            palette = "INK_GREEN",
            nightMode = true,
            backgroundDim = 0.75f,
            background = "background_1761100000000.jpg",
            tags = listOf("午饭", "咖啡")
        ),
        tasks = listOf(
            BackupTask("2026-10-21", "写周报, 三个项目各一行", 1, "DONE", 100L, 200L, 200L),
            BackupTask("2026-10-21", "第二行\n带换行的事", 2, "PENDING", 300L, null, 300L)
        ),
        expenses = listOf(
            BackupExpense("2026-10-21", 1250L, "午饭", 100L, 100L),
            BackupExpense("2026-10-20", 5L, "", 50L, 50L)
        ),
        journal = listOf(
            BackupJournal("2026-10-21", "今天\n多云, 心情一般", 3, "journal_2026-10-21_1.jpg", 400L, 500L)
        )
    )

    private val imageBytes = ByteArray(64) { (it * 3).toByte() }

    /** 写出一份完整备份（含一张配图），返回文件。同一个用例里可以重复调 */
    private fun writeSample(): File {
        val imagesDir = File(temp.root, "src-journal").apply { mkdirs() }
        val image = File(imagesDir, "journal_2026-10-21_1.jpg").apply { writeBytes(imageBytes) }
        val target = File(temp.root, "backup.zip")
        BackupWriter.write(
            target = target,
            payload = sample,
            images = listOf(ImageFile(BackupFormat.DIR_JOURNAL + image.name, image))
        )
        return target
    }

    @Test
    fun `写出去再读回来 每个字段都应一致`() {
        val staged = BackupReader.read(writeSample())

        assertEquals(sample.tasks, staged.payload.tasks)
        assertEquals(sample.expenses, staged.payload.expenses)
        assertEquals(sample.journal, staged.payload.journal)
        assertEquals(sample.settings, staged.payload.settings)
        assertEquals(sample.appVersion, staged.payload.appVersion)
        assertEquals(sample.exportedAt, staged.payload.exportedAt)
    }

    @Test
    fun `摘要里的数量与包里实际数量一致`() {
        val summary = BackupReader.read(writeSample()).summary

        assertEquals(2, summary.taskCount)
        assertEquals(2, summary.expenseCount)
        assertEquals(1, summary.journalCount)
        assertEquals(1, summary.imageCount)
        assertTrue(summary.hasSettings)
        assertEquals(BackupFormat.VERSION, summary.formatVersion)
    }

    @Test
    fun `csv 带 UTF-8 BOM`() {
        val csv = readEntry(writeSample(), BackupFormat.ENTRY_EXPENSES_CSV)

        // 没有 BOM 的话，Windows 上的 Excel 会按 GBK 解 UTF-8，中文全是乱码
        assertEquals(0xEF.toByte(), csv[0])
        assertEquals(0xBB.toByte(), csv[1])
        assertEquals(0xBF.toByte(), csv[2])
    }

    @Test
    fun `正文里的逗号与换行不会把 csv 撑成好几列`() {
        val tasksCsv = readEntry(writeSample(), BackupFormat.ENTRY_TASKS_CSV)
            .toString(Charsets.UTF_8)
        val journalCsv = readEntry(writeSample(), BackupFormat.ENTRY_JOURNAL_CSV)
            .toString(Charsets.UTF_8)

        // 带 ASCII 逗号的单元格必须整体被引号包住
        assertTrue(
            "任务正文没被引号包住：\n$tasksCsv",
            tasksCsv.contains("\"写周报, 三个项目各一行\"")
        )
        // 带换行的单元格同理，否则一行会变成两行
        assertTrue(
            "日记正文没被引号包住：\n$journalCsv",
            journalCsv.contains("\"今天\n多云, 心情一般\"")
        )
    }

    @Test
    fun `金额在 csv 里是元 且由分精确换算`() {
        val expensesCsv = readEntry(writeSample(), BackupFormat.ENTRY_EXPENSES_CSV)
            .toString(Charsets.UTF_8)

        assertTrue("12.50 没出现：\n$expensesCsv", expensesCsv.contains("12.50"))
        assertTrue("0.05 没出现：\n$expensesCsv", expensesCsv.contains("0.05"))
    }

    @Test
    fun `包里的图片能解出来且字节一致`() {
        val file = writeSample()
        val themeDir = File(temp.root, "out-theme")
        val journalDir = File(temp.root, "out-journal")

        val restored = BackupReader.extractImages(file, themeDir, journalDir)
        assertEquals(1, restored)

        val image = File(journalDir, "journal_2026-10-21_1.jpg")
        assertTrue("图片没落到日记目录", image.exists())
        assertTrue("图片内容被改了", image.readBytes().contentEquals(imageBytes))
    }

    @Test
    fun `不是备份的 zip 会被拒`() {
        val file = File(temp.root, "random.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("hello.txt"))
            zip.write("hi".toByteArray())
            zip.closeEntry()
        }

        val error = runCatching { BackupReader.read(file) }.exceptionOrNull()
        assertTrue("应该抛 BackupFormatException，实际是 $error", error is BackupFormatException)
        // 提示要说人话，不能是 NPE 或 ZipException 的原文
        assertNotNull(error?.message)
        assertTrue("提示看不懂：${error?.message}", error!!.message!!.contains("data.json"))
    }

    @Test
    fun `根本不是 zip 的文件会被拒`() {
        val file = File(temp.root, "garbage.bin").apply { writeBytes(ByteArray(100) { 7 }) }
        val error = runCatching { BackupReader.read(file) }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
    }

    @Test
    fun `更新版本的备份被拒 且提示去升级`() {
        val file = File(temp.root, "future.zip")
        val json = """{"format":"tonight-backup","formatVersion":99,"appVersion":"9.9.9"}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.ENTRY_DATA))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val error = runCatching { BackupReader.read(file) }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
        assertTrue("提示里没让用户去升级：${error?.message}", error!!.message!!.contains("更新"))
    }

    @Test
    fun `缺 format 字段的 json 会被拒`() {
        val file = File(temp.root, "foreign.zip")
        val json = """{"formatVersion":1,"tasks":[]}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.ENTRY_DATA))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        val error = runCatching { BackupReader.read(file) }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
    }

    // ── 工具 ────────────────────────────────────────────────

    private fun readEntry(zipFile: File, name: String): ByteArray =
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(name)
            assertNotNull("包里没有 $name", entry)
            zip.getInputStream(entry).readBytes()
        }
}
