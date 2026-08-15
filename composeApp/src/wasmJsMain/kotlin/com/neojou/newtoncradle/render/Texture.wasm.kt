package com.neojou.newtoncradle.render

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun decodeImageBytes(bytes: ByteArray): RgbaTex? {
    val image = Image.makeFromEncoded(bytes) ?: return null
    val w = image.width
    val h = image.height
    val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bmp = Bitmap()
    if (!bmp.allocPixels(info)) return null
    if (!image.readPixels(bmp)) return null
    val dst = bmp.readPixels() ?: return null
    val rgb = IntArray(w * h)
    var p = 0
    for (i in rgb.indices) {
        val r = dst[p].toInt() and 0xFF
        val g = dst[p + 1].toInt() and 0xFF
        val b = dst[p + 2].toInt() and 0xFF
        rgb[i] = (r shl 16) or (g shl 8) or b
        p += 4
    }
    return RgbaTex(w, h, rgb)
}
