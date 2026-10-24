package com.qingji.plan.util

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * 识图功能里「把图片变成 data URL」这一段。
 *
 * 用 Robolectric 跑，因为 `BitmapFactory` / `Bitmap.compress` 是真的 Android 类。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageUtilsTest {

    // ---------- dataUrl：纯函数 ----------

    @Test
    fun `dataUrl 前缀是标准的 data URI`() {
        val url = ImageUtils.dataUrl("image/jpeg", byteArrayOf(1, 2, 3))
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun `dataUrl 用的是不带换行的标准 base64`() {
        // NO_WRAP 很重要：带换行的 base64 塞进 JSON 里虽然是合法的，
        // 但有些服务端会当成坏数据；java.util.Base64 的 basic encoder 天然不换行
        val bytes = ByteArray(200) { it.toByte() }
        val encoded = ImageUtils.dataUrl("image/png", bytes).substringAfter(",")
        assertTrue("不该有换行", !encoded.contains('\n'))
        assertTrue("不该有换行", !encoded.contains('\r'))
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes), encoded)
    }

    @Test
    fun `空字节也能拼出合法前缀`() {
        assertEquals("data:image/jpeg;base64,", ImageUtils.dataUrl("image/jpeg", ByteArray(0)))
    }

    // ---------- decodeScaled：真图 ----------

    /** 画一张纯色图，压成 PNG 字节 */
    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    @Test
    fun `大图会被压到长边不超过上限`() {
        val big = pngBytes(3000, 2000)

        val scaled = ImageUtils.decodeScaled(big, maxDim = 1600)

        assertNotNull(scaled)
        assertTrue(
            "长边还是 ${maxOf(scaled!!.width, scaled.height)}，没压下来",
            maxOf(scaled.width, scaled.height) <= 1600,
        )
        // 长宽比不能变
        assertEquals(1.5f, scaled.width.toFloat() / scaled.height, 0.02f)
        scaled.recycle()
    }

    @Test
    fun `本来就小的图不放大`() {
        val small = pngBytes(400, 300)

        val scaled = ImageUtils.decodeScaled(small, maxDim = 1600)

        assertNotNull(scaled)
        assertEquals(400, scaled!!.width)
        assertEquals(300, scaled.height)
        scaled.recycle()
    }

    @Test
    fun `不是图片的字节不抛异常`() {
        // ⚠️ 这里只能断言「不抛」，断言不了「返回 null」——
        // Robolectric 的 BitmapFactory 影子实现很宽松，喂垃圾字节它也可能返回一张假图。
        // 真正要守的是「不崩」：`ImageUtils.toDataUrl` 已经把 null 当成
        // 「这张图片解不开」的错误处理掉了，所以解析失败不会把 App 带崩。
        val garbage = runCatching { ImageUtils.decodeScaled("这不是一张图".toByteArray()) }
        val empty = runCatching { ImageUtils.decodeScaled(ByteArray(0)) }

        assertTrue("喂垃圾字节抛异常了：${garbage.exceptionOrNull()}", garbage.isSuccess)
        assertTrue("喂空字节抛异常了：${empty.exceptionOrNull()}", empty.isSuccess)
        // 万一影子实现真返回了图，顺手回收掉
        listOfNotNull(garbage.getOrNull(), empty.getOrNull()).forEach { it.recycle() }
    }

    // ---------- encodeJpeg ----------

    @Test
    fun `压出来的确实是 JPEG`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val jpeg = ImageUtils.encodeJpeg(bitmap)
        bitmap.recycle()

        // JPEG 的魔数：FF D8 开头，FF D9 结尾
        assertTrue("开头不是 JPEG 魔数", jpeg.size > 4 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())
        assertEquals(0xD9.toByte(), jpeg[jpeg.size - 1])
    }

    @Test
    fun `质量越低体积越小`() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        // 画点花样，纯色图怎么压都一样大
        for (x in 0 until 600 step 3) {
            for (y in 0 until 600 step 3) {
                bitmap.setPixel(x, y, if ((x + y) % 6 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        val high = ImageUtils.encodeJpeg(bitmap, 95)
        val low = ImageUtils.encodeJpeg(bitmap, 40)
        bitmap.recycle()

        assertTrue("低质量(${low.size}) 应该比 高质量(${high.size}) 小", low.size < high.size)
    }
}
