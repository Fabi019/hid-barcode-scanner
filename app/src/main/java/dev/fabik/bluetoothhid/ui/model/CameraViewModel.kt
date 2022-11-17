package dev.fabik.bluetoothhid.ui.model

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class CameraViewModel : ViewModel() {
    companion object {
        val SD_480P = Size(480, 640)
        val HD_720P = Size(720, 960)
        val FHD_1080P = Size(1080, 1440)
    }

    private var lastBarCodeValue: String? = null

    var currentBarCode by mutableStateOf<Barcode?>(null)
    var focusTouchPoint by mutableStateOf<Offset?>(null)
    var isFocusing by mutableStateOf(false)

    private var lastSourceRes: Size? = null

    var scale = 1f
    var transX = 0f
    var transY = 0f
    var scanRect = Rect.Zero

    fun updateScale(source: Size, previewView: PreviewView) {
        if (lastSourceRes != source) {
            val vw = previewView.width.toFloat()
            val vh = previewView.height.toFloat()

            val sw = source.width.toFloat()
            val sh = source.height.toFloat()

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

            lastSourceRes = source
        }
    }

    fun filterBarCodes(
        barcodes: List<Barcode>,
        fullyInside: Boolean,
        useRawValue: Boolean
    ): String? {
        var result: String? = null

        val filtered = barcodes.filter {
            it.cornerPoints?.map { p ->
                Offset(p.x * scale - transX, p.y * scale - transY)
            }?.forEach { o ->
                if (fullyInside) {
                    if (!scanRect.contains(o)) {
                        return@filter false
                    }
                } else {
                    if (scanRect.contains(o)) {
                        return@filter true
                    }
                }
            }
            fullyInside
        }

        filtered.firstOrNull().let {
            if (useRawValue) {
                it?.rawValue
            } else {
                it?.displayValue
            }?.let { value ->
                if (lastBarCodeValue != value) {
                    lastBarCodeValue = value
                    result = value
                }
            }
            currentBarCode = it
        }

        return result
    }

    suspend fun PointerInputScope.focusOnTap(
        cameraControl: CameraControl,
        previewView: PreviewView
    ) = detectTapGestures {
        if (!isFocusing) {
            focusTouchPoint = it
            isFocusing = true

                scope.launch {
                    focusCircleRadius.snapTo(100f)
                    focusCircleRadius.animateTo(
                        80f, spring(Spring.DampingRatioMediumBouncy)
                    )
                }

                val meteringPointFactory = SurfaceOrientedMeteringPointFactory(
                    size.width.toFloat(),
                    size.height.toFloat()
                )

                val meteringAction = FocusMeteringAction.Builder(
                    meteringPointFactory.createPoint(it.x, it.y),
                    FocusMeteringAction.FLAG_AF
                ).disableAutoCancel().build()

            cameraControl.startFocusAndMetering(meteringAction)
                .addListener({
                    isFocusing = false
                }, Executors.newSingleThreadExecutor())
        }
        }

}
