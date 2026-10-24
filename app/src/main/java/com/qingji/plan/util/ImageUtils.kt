package com.qingji.plan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

/**
 * 把用户选的图片变成能塞进 OpenAI 多模态请求体的 data URL。
 *
 * 为什么要压：手机截图动辄 1080×2400、两三兆，base64 之后还要再涨三分之一。
 * DeepSeek 那侧单图上限 32 MiB、请求体 48 MiB，不压虽然也能过，但上传慢、也浪费流量。
 *
 * 压到多少合适：DeepSeek 官方说图片**进模型前会自动缩放**——小于约 544×544 的会被放大，
 * 更大的会缩到约 1300×1300 总像素，而且**每张图最多算 1024 token**。
 * 所以压到 1600 已经超出它实际用的分辨率了，纯属留余量；再往上压没有意义。
 */
object ImageUtils {

    /** 长边压到这个尺寸 */
    const val MAX_DIM = 1600

    /** JPEG 质量。课表是白底黑字，85 足够清晰，再高只是变大 */
    const val JPEG_QUALITY = 85

    /**
     * 拼 `data:<mime>;base64,<...>`。
     *
     * 纯函数，不碰 Android，所以能直接单测。用的是 `java.util.Base64`（API 26+）而不是
     * `android.util.Base64`——正好 minSdk 是 26，而且 JVM 测试里也能跑。
     */
    fun dataUrl(mime: String, bytes: ByteArray): String =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)

    /**
     * 按长边 [maxDim] 降采样解码。
     *
     * `inSampleSize` 只能取 2 的幂，所以先粗缩一轮，剩下那点再精确缩一次。
     * 解不开（不是图片 / 格式不支持）返回 null，不抛。
     */
    fun decodeScaled(bytes: ByteArray, maxDim: Int = MAX_DIM): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= maxDim) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val current = maxOf(decoded.width, decoded.height)
        if (current <= maxDim) return decoded

        val scale = maxDim.toFloat() / current
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    /** 压成 JPEG 字节 */
    fun encodeJpeg(bitmap: Bitmap, quality: Int = JPEG_QUALITY): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /**
     * 完整链路：读 uri → 降采样 → JPEG → data URL。
     *
     * **只能在 IO 线程调**（要读流 + 解码 + 压缩）。
     * 失败时抛带中文说明的 [IllegalStateException]，调用方直接把它显示给用户就行。
     */
    fun toDataUrl(context: Context, uri: Uri): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("读不到这张图片，可能没给相册权限")
        val bitmap = decodeScaled(raw) ?: error("这张图片解不开，换一张试试")
        val jpeg = try {
            encodeJpeg(bitmap)
        } finally {
            bitmap.recycle()
        }
        return dataUrl("image/jpeg", jpeg)
    }
}
