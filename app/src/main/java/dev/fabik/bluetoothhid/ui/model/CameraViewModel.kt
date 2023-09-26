package dev.fabik.bluetoothhid.ui.model

import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.BuildConfig
import java.util.concurrent.Executors

class CameraViewModel : ViewModel() {
    companion object {
        val SD_480P = Size(640, 480)
        val HD_720P = Size(960, 720)
        val FHD_1080P = Size(1440, 1080)
        val UHD_2160P = Size(2160, 1440)
    }

    private var lastBarCodeValue: String? = null

    var currentBarCode by mutableStateOf<Barcode?>(null)
    var focusTouchPoint by mutableStateOf<Offset?>(null)
    var isFocusing by mutableStateOf(false)
    var possibleBarcodes = mutableStateListOf<Barcode>()

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

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setupFocusMode(
        fixExposure: Boolean,
        focusMode: Int,
        ext: Camera2Interop.Extender<ImageAnalysis>
    ) {

        if (fixExposure) {
            // Sets a fixed exposure compensation and iso for the image
            ext.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 1600)
            ext.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                -8
            )
        }

        when (focusMode) {
            // Manual mode
            1 -> {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO
                )
            }

            // Macro mode
            2 -> {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_MACRO
                )
            }

            // Continuous mode
            3 -> {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }

            // EDOF mode
            4 -> {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_EDOF
                )
            }

            // Infinity
            5 -> {
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                ext.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    0.0f
                )
            }
        }
    }

    fun filterBarCodes(
        barcodes: List<Barcode>,
        fullyInside: Boolean,
        useRawValue: Boolean,
        regex: Regex?,
    ): String? {
        var result: String? = null

        val filtered = barcodes.filter {
            // Filter out codes without value
            it.rawBytes != null && !it.rawValue.isNullOrEmpty() && !it.displayValue.isNullOrEmpty()
        }.filter {
            // Filter if they are within the scan area
            it.cornerPoints?.map { p ->
                Offset(p.x * scale - transX, p.y * scale - transY)
            }?.forEach { o ->
                if (fullyInside && !scanRect.contains(o)) {
                    return@filter false
                } else if (scanRect.contains(o)) {
                    return@filter true
                }
            }
            fullyInside
        }.filter {
            // Filter by regex
            val value = if (useRawValue) {
                it.rawValue
            } else {
                it.displayValue
            }
            regex?.matches(value!!) ?: true
        }

        possibleBarcodes = barcodes.filter {
            it.rawBytes == null || it.rawValue.isNullOrEmpty() || it.displayValue.isNullOrEmpty()
        }.toMutableStateList()

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
                    // Add barcode to history
                    HistoryViewModel.addHistoryItem(it)
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

    /*
     * Debug methods for testing FPS and latency of the detector and camera
     */

    private var lastTimestamp = 0L
    var detectorLatency by mutableStateOf(0L)

    fun updateDetectorFPS() {
        if (!BuildConfig.DEBUG) {
            return
        }

        val now = System.currentTimeMillis()
        detectorLatency = now - lastTimestamp
        lastTimestamp = now
    }

    private var lastCameraTimestamp = 0L
    private var lastCameraLatencyTimestamp = 0L
    private var fpsCountCamera = 0
    var fpsCamera by mutableStateOf(0)
    var latencyCamera by mutableStateOf(0L)

    fun updateCameraFPS() {
        if (!BuildConfig.DEBUG) {
            return
        }

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
