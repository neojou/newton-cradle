package com.neojou.newtoncradle.render

import androidx.compose.ui.graphics.ImageBitmap

expect fun imageBitmapFromRgba(width: Int, height: Int, rgba: ByteArray): ImageBitmap

expect fun isWasmTarget(): Boolean
