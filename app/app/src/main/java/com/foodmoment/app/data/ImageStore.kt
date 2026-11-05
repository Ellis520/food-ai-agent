package com.foodmoment.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 图片预处理与缓存。
 *
 * 社区帖子图片存在服务端（需会话鉴权、no-store），本地只做磁盘缓存；
 * 拍照识别只做压缩转 Base64，不落盘。
 */
class ImageStore(private val context: Context) {

    private val outboxDir: File
        get() = File(context.filesDir, "community_outbox").apply { if (!exists()) mkdirs() }

    private val cacheDir: File
        get() = File(context.filesDir, "community_cache").apply { if (!exists()) mkdirs() }

    /** 解码并等比缩小到长边不超过 maxSide */
    fun decodeUri(uri: Uri, maxSide: Int = 1280): Bitmap? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
            sample *= 2
        }

        return runCatching {
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        }.getOrNull()
    }

    /** 转 Base64（不含 data: 前缀），供视觉模型使用 */
    fun toBase64(uri: Uri, maxSide: Int = 1280, quality: Int = 80): String? {
        val bitmap = decodeUri(uri, maxSide) ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 社区发帖图：长边 1280、JPEG。
     * 服务端单张上限 750KB，但 base64 会膨胀约 33%，所以压到 450KB 留余量。
     */
    fun saveCommunityImage(uri: Uri, key: String, index: Int): File? {
        val bitmap = decodeUri(uri, maxSide = 1280) ?: return null
        val target = File(outboxDir, "${key}_$index.jpg")
        var quality = 80
        while (quality >= 40) {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            if (target.length() <= 450 * 1024) break
            quality -= 15
        }
        bitmap.recycle()
        return target
    }

    fun outboxFile(name: String): File = File(outboxDir, name)

    fun deleteOutboxPrefix(prefix: String) {
        runCatching {
            outboxDir.listFiles()?.forEach { if (it.name.startsWith(prefix)) it.delete() }
        }
    }

    /** 社区图片缓存：服务端返回 no-store，必须自己落盘，否则每次滚动都要重新请求 */
    fun communityCacheFile(postId: Long, position: Int): File =
        File(cacheDir, "p${postId}_$position.jpg")

    fun clearCommunityCache() {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
        runCatching { outboxDir.listFiles()?.forEach { it.delete() } }
    }
}
