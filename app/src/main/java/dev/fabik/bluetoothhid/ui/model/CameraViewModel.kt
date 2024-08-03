package dev.fabik.bluetoothhid.ui.model

import android.hardware.camera2.CaptureRequest
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.common.Barcode
import dev.fabik.bluetoothhid.utils.JsEngineService

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

    // TODO: remove - no longer used for transformation
    var scale = 1f
    var transX = 0f
    var transY = 0f
    var scanRect = Rect.Zero

    fun updateScale(source: Size, previewView: PreviewView) {
        if (lastSourceRes != source) {
            lastSourceRes = source
            lastPreviewRes = Size(previewView.width, previewView.height)
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun setupFocusMode(
        control: CameraControl,
        fixExposure: Boolean,
        focusMode: Int,
    ) {
        Camera2CameraControl.from(control).let {
            CaptureRequestOptions.Builder().apply {
                if (fixExposure) {
                    // Sets a fixed exposure compensation and iso for the image
                    setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 1600)
                    setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                        -8
                    )
                    //setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                }

                if (focusMode > 0) {
                    setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE, when (focusMode) {
                            1 -> CaptureRequest.CONTROL_AF_MODE_AUTO // Manual mode
                            2 -> CaptureRequest.CONTROL_AF_MODE_MACRO // Macro mode
                            3 -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO // Continuous mode
                            4 -> CaptureRequest.CONTROL_AF_MODE_EDOF // EDOF mode
                            5 -> CaptureRequest.CONTROL_AF_MODE_OFF // Infinity
                            else -> it.captureRequestOptions.getCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE)
                                ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        }
                    )

                    if (focusMode == 5) {
                        setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
                    }
                }
            }.let { builder ->
                it.addCaptureRequestOptions(builder.build())
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
            }?.let { v ->
                var value = v

                regex?.let { re ->
                    // extract first capture group if it exists
                    re.find(value)?.let { match ->
                        match.groupValues.getOrNull(1)?.let { group ->
                            value = group
                        }
                    }
                }

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

    suspend fun mapWithJs(
        jsEngineService: JsEngineService.LocalBinder?,
        barcode: Barcode,
        value: String,
        js: String
    ): String {
        return jsEngineService?.evaluateTemplate(
            js,
            value,
            HistoryViewModel.parseBarcodeType(barcode.format)
        ) ?: value
    }

    /*
     * Debug methods for testing FPS and latency of the detector and camera
     */

    class BoundedList(val maxSize: Int) : Iterable<Float> {
        var internalArray = FloatArray(maxSize) { Float.NaN }
            private set
        private var tail = 0
        private var head: Int? = null

        fun addLast(element: Float) {
            if (head == null) {
                head = tail
            } else if (head == tail) {
                head = (head!! + 1) % maxSize
            }
            internalArray[tail] = element
            tail = (tail + 1) % maxSize
        }

        override fun iterator() = object : Iterator<Float> {
            private val current = head ?: 0
            private var count = 0 // Keeps track of iterated elements

            override fun hasNext() =
                count < maxSize && !internalArray[(current + count) % maxSize].isNaN()

            override fun next(): Float = internalArray[(current + count++) % maxSize]
        }
    }

    private var lastTimestamp = 0L
    var detectorLatency by mutableLongStateOf(0L)

    var detectorLatencies by mutableStateOf(BoundedList(100))
    var cameraLatencies by mutableStateOf(BoundedList(100))

    fun updateDetectorFPS() {
        val now = System.currentTimeMillis()
        detectorLatency = now - lastTimestamp
        lastTimestamp = now

        detectorLatencies.addLast(detectorLatency.toFloat())
    }

    private var lastCameraTimestamp = 0L
    private var lastCameraLatencyTimestamp = 0L
    private var fpsCountCamera = 0
    var fpsCamera by mutableIntStateOf(0)
    var latencyCamera by mutableLongStateOf(0L)

    fun updateCameraFPS() {
        val now = System.currentTimeMillis()

        latencyCamera = now - lastCameraLatencyTimestamp
        lastCameraLatencyTimestamp = now

        cameraLatencies.addLast(latencyCamera.toFloat())

        if (now - lastCameraTimestamp > 1000) {
            lastCameraTimestamp = now
            fpsCamera = fpsCountCamera
            fpsCountCamera = 0
        } else {
            fpsCountCamera++
        }
    }

}
