package dev.fabik.bluetoothhid.utils

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import kotlin.math.min


object ImageUtils {
    // @see: androidx.camera.core.internal.utils.ImageUtil.yuv_420_888toNv21()
    fun yuv420888toNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer.also { it.rewind() }
        val uBuffer = uPlane.buffer.also { it.rewind() }
        val vBuffer = vPlane.buffer.also { it.rewind() }

        val ySize = yBuffer.remaining()

        var position = 0
        val nv21 = ByteArray(ySize + (image.width * image.height / 2))

        // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
        for (row in 0 until image.height) {
            yBuffer[nv21, position, image.width]
            position += image.width
            yBuffer.position(
                min(
                    ySize.toDouble(),
                    (yBuffer.position() - image.width + yPlane.rowStride).toDouble()
                )
                    .toInt()
            )
        }

        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
        // perform faster bulk gets from the byte buffers.
        val vLineBuffer = ByteArray(vRowStride)
        val uLineBuffer = ByteArray(uRowStride)
        for (row in 0 until chromaHeight) {
            vBuffer[vLineBuffer, 0, min(
                vRowStride.toDouble(),
                vBuffer.remaining().toDouble()
            ).toInt()]
            uBuffer[uLineBuffer, 0, min(
                uRowStride.toDouble(),
                uBuffer.remaining().toDouble()
            ).toInt()]
            var vLineBufferPosition = 0
            var uLineBufferPosition = 0
            for (col in 0 until chromaWidth) {
                nv21[position++] = vLineBuffer[vLineBufferPosition]
                nv21[position++] = uLineBuffer[uLineBufferPosition]
                vLineBufferPosition += vPixelStride
                uLineBufferPosition += uPixelStride
            }
        }

        return nv21
    }

    fun yuv420888toARGB8888(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer.also { it.rewind() }
        val uBuffer = uPlane.buffer.also { it.rewind() }
        val vBuffer = vPlane.buffer.also { it.rewind() }

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * yRowStride + x
                val yValue = (yBuffer[yIndex].toInt() and 0xFF)

                val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                val uValue = (uBuffer[uvIndex].toInt() and 0xFF) - 128
                val vValue = (vBuffer[uvIndex].toInt() and 0xFF) - 128

                val yF = yValue.toFloat()
                val r = (yF + 1.370705f * vValue).toInt().coerceIn(0, 255)
                val g = (yF - 0.337633f * uValue - 0.698001f * vValue).toInt().coerceIn(0, 255)
                val b = (yF + 1.732446f * uValue).toInt().coerceIn(0, 255)

                argb[y * width + x] =
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }

}