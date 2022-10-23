package dev.fabik.bluetoothhid.ui.model

import android.util.Size
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode

class CameraViewModel : ViewModel() {
    companion object {
        val SD_480P = Size(480, 640)
        val HD_720P = Size(720, 960)
        val FHD_1080P = Size(1080, 1440)
    }

    var lastBarCodeValue by mutableStateOf<String?>(null)
    var currentBarCode by mutableStateOf<Barcode?>(null)
    var focusTouchPoint by mutableStateOf<Offset?>(null)

    val focusCircleAlpha = Animatable(0f)
    val focusCircleRadius = Animatable(100f)

    var sourceRes: Size? = null
    var scale = 1f
    var transX = 0f
    var transY = 0f
    var scanRect = Rect.Zero

    fun updateScale(sw: Float, sh: Float, vw: Float, vh: Float) {
        val viewAspectRatio = vw / vh
        val sourceAspectRatio = sw / sh

        if (sourceAspectRatio > viewAspectRatio) {
            scale = vh / sh
            transX = (sw * scale - vw) / 2
            transY = 0f
        } else {
            scale = vw / sw
            transX = 0f
            transY = (sh * scale - vh) / 2
        }
    }
}
