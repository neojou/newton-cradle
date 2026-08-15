package com.neojou.newtoncradle.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun imageBitmapFromRgba(width: Int, height: Int, rgba: ByteArray): ImageBitmap {
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
    return Image.makeRaster(info, rgba, width * 4).toComposeImageBitmap()
}

actual fun isWasmTarget(): Boolean = true
