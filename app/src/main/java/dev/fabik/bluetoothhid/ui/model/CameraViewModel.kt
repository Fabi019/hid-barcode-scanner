package dev.fabik.bluetoothhid.ui.model

import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
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

    var lastSourceRes: Size? = null
    var lastPreviewRes: Size? = null

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
            lastPreviewRes = Size(previewView.width, previewView.height)
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

        currentBarCode = filtered.firstOrNull()

        currentBarCode?.let {
            if (useRawValue) {
                it.rawValue
            } else {
                it.displayValue
            }.let { value ->
                if (lastBarCodeValue != value) {
                    lastBarCodeValue = value
                    result = value
                }
            }
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

            val factory = previewView.meteringPointFactory

            val meteringAction = FocusMeteringAction.Builder(
                factory.createPoint(it.x, it.y),
                FocusMeteringAction.FLAG_AF
            ).disableAutoCancel().build()

            cameraControl.startFocusAndMetering(meteringAction)
                .addListener({
                    isFocusing = false
                }, Executors.newSingleThreadExecutor())
        }
    }

    suspend fun PointerInputScope.zoomGesture(
        cameraInfo: CameraInfo,
        cameraControl: CameraControl
    ) = detectTransformGestures(true) { _, _, zoom, _ ->
        val currentZoom = cameraInfo.zoomState.value
        val currentZoomRatio = currentZoom?.zoomRatio ?: 1f

        val newZoomRatio = (currentZoomRatio * zoom).coerceIn(
            currentZoom?.minZoomRatio ?: 1f,
            currentZoom?.maxZoomRatio ?: 1f
        )

        cameraControl.setZoomRatio(newZoomRatio)
    }

    private var lastTimestamp = 0L
    var detectorLatency = 0L

    fun updateDetectorFPS() {
        val now = System.currentTimeMillis()
        detectorLatency = now - lastTimestamp
        lastTimestamp = now
    }

    private var lastCameraTimestamp = 0L
    private var lastCameraLatencyTimestamp = 0L
    private var fpsCountCamera = 0
    var fpsCamera = 0
    var latencyCamera = 0L

    fun updateCameraFPS() {
        val now = System.currentTimeMillis()

        latencyCamera = now - lastCameraLatencyTimestamp
        lastCameraLatencyTimestamp = now

        if (now - lastCameraTimestamp > 1000) {
            lastCameraTimestamp = now
            fpsCamera = fpsCountCamera
            fpsCountCamera = 0
        } else {
            fpsCountCamera++
        }
    }

}
