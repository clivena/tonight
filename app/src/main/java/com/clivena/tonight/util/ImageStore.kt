package com.clivena.tonight.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片的存取。
 *
 * 三条硬规则，每一条都对应一个真实的坑：
 *
 * 1. **只存文件名，不存绝对路径。** 应用私有目录的绝对路径里带包名和用户 id，
 *    换设备、恢复备份、甚至换个安装方式都会变。存文件名，目录每次现算。
 * 2. **入库前一定先采样、再压缩。** 一张手机原图 4000×3000 解出来是 48MB，
 *    直接 decode 就是 OOM，崩的时机还正好在「选完图回到界面」那一刻。
 * 3. **EXIF 旋转要自己补。** BitmapFactory 不认 EXIF，竖着拍的照片
 *    解出来会横躺着 —— 用户会以为是 App 把图弄坏了。
 */
object ImageStore {

    const val DIR_THEME = "theme"
    const val DIR_JOURNAL = "journal"

    /**
     * 背景图每次换都拿一个新名字。
     *
     * 不用固定的 background.jpg：名字不变的话，「换了一张图」在界面上
     * 体现不出来 —— 加载逻辑按文件名做 key，文件名没变就不会重新解码。
     * 代价是换图时必须手动删旧文件（见 SettingsScreen）。
     */
    fun newBackgroundName(): String = "background_${System.currentTimeMillis()}.jpg"

    /**
     * 长边上限。
     *
     * 这个数不是「随便压一下省点空间」，它直接决定背景图看着清不清楚：
     * 背景图是 `ContentScale.Crop` 铺满整屏的，铺满 1080×2400 的竖屏时，
     * 一张 4:3 横图的**高**必须 >= 2400（即长边 >= 3200）才不需要放大。
     *
     * 以前是 1600 → 图高只有 1200，得放大 2 倍去铺屏，怎么调遮罩都是软的。
     * 现在 2560 → 图高 1920，放大约 1.25 倍，肉眼基本看不出插值。
     *
     * 代价是内存：1600×1200 约 7.3MB，2560×1920 约 18.7MB（ARGB_8888）。
     * 一张全屏背景换这个清晰度，值。
     */
    private const val MAX_DIMEN = 2560
    private const val JPEG_QUALITY = 85

    /** 目录：filesDir/<dir>/，不存在就建 */
    private fun dir(context: Context, name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    /**
     * 把相册里的图搬进私有目录，返回**文件名**。
     *
     * @return null 表示这张图读不出来（不是图片、流打不开、内存不足）
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        dirName: String,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}.tmp")
        try {
            // 先原样落到临时文件 —— EXIF 只能从文件读，而 content:// 的流只能读一遍
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null

            val rotation = readRotation(temp)
            val bitmap = decodeSampledFile(temp, MAX_DIMEN) ?: return@withContext null
            val upright = if (rotation == 0) bitmap else rotate(bitmap, rotation)

            val target = File(dir(context, dirName), fileName)
            target.outputStream().use { out ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (upright !== bitmap) upright.recycle()
            bitmap.recycle()
            fileName
        } catch (e: Exception) {
            // 选图失败是很常见的事（文件被删、格式怪、内存紧）。
            // 这里的取舍是「不出声地失败」——外面看到返回 null 就保持原样，
            // 比弹一个用户看不懂的异常有用。
            null
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** 读私有目录里的图，按 [maxDimen] 采样。主线程别调。 */
    suspend fun load(
        context: Context,
        dirName: String,
        fileName: String?,
        maxDimen: Int = MAX_DIMEN
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (fileName.isNullOrBlank()) return@withContext null
        val file = File(dir(context, dirName), fileName)
        if (!file.exists()) return@withContext null
        decodeSampledFile(file, maxDimen)?.asImageBitmap()
    }

    fun delete(context: Context, dirName: String, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        File(dir(context, dirName), fileName).takeIf { it.exists() }?.delete()
    }

    /** 背景图换成新的之前，先把旧的删掉；同名覆盖是另一条路，这里只处理改名的情况 */
    fun deleteBackground(context: Context, fileName: String?) {
        delete(context, DIR_THEME, fileName)
    }

    // ── 内部 ────────────────────────────────────────────────

    private fun decodeSampledFile(file: File, maxDimen: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimen || bounds.outHeight / sample > maxDimen) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    @Suppress("DEPRECATION")
    private fun readRotation(file: File): Int = try {
        when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap =
        Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true
        )
}
