package dev.fabik.bluetoothhid.ui.model

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.toPointF
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.LatencyTrace
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
class NewCameraViewModel : ViewModel() {
    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var surfaceMeteringPointFactory: SurfaceOrientedMeteringPointFactory? = null
    private var cameraControl: CameraControl? = null
    private var barcodeAnalyzer: ZXingAnalyzer? = null

    private var onBarcodeDetected: (String, Int) -> Unit = { _, _ -> }

    var scanRect = Rect.Zero
    var overlayPosition by mutableStateOf<Offset?>(null)
    var overlaySize by mutableStateOf<androidx.compose.ui.geometry.Size?>(null)

    val cameraTrace = LatencyTrace(100)
    val detectorTrace = LatencyTrace(100)

    private val cameraPreviewUseCase =
        Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build().apply {
                setSurfaceProvider { newSurfaceRequest ->
                    _surfaceRequest.update { newSurfaceRequest }
                    surfaceMeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                        newSurfaceRequest.resolution.width.toFloat(),
                        newSurfaceRequest.resolution.height.toFloat(),
                    )
                }
            }

    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        frontCamera: Boolean,
        resolution: Int,
        codeTypes: Set<String>,
        onCameraReady: (CameraControl?, CameraInfo?) -> Unit,
        onBarcode: (String) -> Unit,
    ) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        val options = BarcodeReader.Options()
        options.formats = codeTypes.mapNotNull { it.toIntOrNull() }
            .map { ZXingAnalyzer.index2Format(it) }.toSet()

        onBarcodeDetected = { value, format ->
            if (value != _lastBarcode) {
                HistoryViewModel.addHistoryItem(value, format)
                onBarcode(value)
                _lastBarcode = value
            }
        }

        barcodeAnalyzer = ZXingAnalyzer(
            options,
            _scanDelay,
            ::onBarcodeAnalyze,
            ::onBarcodeResult
        )

        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
                when (resolution) {
                    3 -> CameraViewModel.UHD_2160P
                    2 -> CameraViewModel.FHD_1080P
                    1 -> CameraViewModel.HD_720P
                    else -> CameraViewModel.SD_480P
                },
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        ).build()

        val analyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageRotationEnabled(true)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analyzer.setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyzer!!)

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(cameraPreviewUseCase)
            .addUseCase(analyzer)
            .build()

        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner,
            if (frontCamera && processCameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup
        )

        cameraControl = camera.cameraControl
        onCameraReady(camera.cameraControl, camera.cameraInfo)

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
            cameraControl = null
            onCameraReady(null, null)
        }
    }

    fun onBarcodeAnalyze() {
        cameraTrace.trigger()
    }

    var viewSize: IntSize? = null
    var lastScanSize: Size? = null
    private var scale = 1.0f
    private var translation = Offset(0.0f, 0.0f)

    fun transformPoint(point: Point, scanSize: Size): PointF {
        if (scanSize != lastScanSize) {
            val vw = viewSize?.width ?: return point.toPointF()
            val vh = viewSize?.height ?: return point.toPointF()

            val sw = scanSize.width.toFloat()
            val sh = scanSize.height.toFloat()

            val viewAspectRatio = vw / vh
            val sourceAspectRatio = sw / sh

            if (sourceAspectRatio > viewAspectRatio) {
                scale = vh / sh
                translation = Offset((sw * scale - vw) / 2, 0f)
            } else {
                scale = vw / sw
                translation = Offset(0f, (sh * scale - vh) / 2)
            }

            lastScanSize = scanSize
        }
        return PointF(point.x * scale - translation.x, point.y * scale - translation.y)
    }

    private var _fullyInside: Boolean = false
    private var _scanRegex: Regex? = null
    private var _jsCode: String? = null
    private var _scanDelay: Int = 0
    private var _jsEngineService: JsEngineService.LocalBinder? = null

    fun updateScanParameters(
        fullyInside: Boolean,
        scanRegex: Regex?,
        jsCode: String?,
        frequency: Int,
        jsEngineService: JsEngineService.LocalBinder?
    ) {
        _scanDelay = when (frequency) {
            0 -> 0
            1 -> 100
            3 -> 1000
            else -> 500
        }
        barcodeAnalyzer?.scanDelay = _scanDelay
        _fullyInside = fullyInside
        _scanRegex = scanRegex
        _jsCode = jsCode
        _jsEngineService = jsEngineService
    }

    private var _lastBarcode: String? = null
    private val _currentBarcode = MutableStateFlow<Barcode?>(null)
    val currentBarcode: StateFlow<Barcode?> = _currentBarcode.asStateFlow()

    data class Barcode(
        var value: String?,
        val cornerPoints: List<PointF>,
        val size: Size,
        val format: BarcodeReader.Format
    )

    fun onBarcodeResult(
        result: List<BarcodeReader.Result>,
        source: Size
    ) {
        detectorTrace.trigger()

        val barcode = result.map {
            val cornerPoints = listOf(
                it.position.topLeft,
                it.position.topRight,
                it.position.bottomRight,
                it.position.bottomLeft
            ).map {
                transformPoint(it, source)
            }

            Barcode(it.text, cornerPoints, source, it.format)
        }.filter {
            when (_fullyInside) {
                true -> it.cornerPoints.all { scanRect.contains(Offset(it.x, it.y)) }
                false -> it.cornerPoints.any { scanRect.contains(Offset(it.x, it.y)) }
            }
        }.filter {
            _scanRegex?.matches(it.value ?: return@filter true) != false
        }.firstOrNull()

        _currentBarcode.update { _ -> barcode }

        barcode?.value?.let {
            _jsEngineService?.let { s ->
                viewModelScope.launch(Dispatchers.IO) {
                    val value =
                        mapJS(s, it, ZXingAnalyzer.format2String(barcode.format))
                    onBarcodeDetected(value, ZXingAnalyzer.format2Index(barcode.format))
                }
            } ?: run {
                onBarcodeDetected(it, ZXingAnalyzer.format2Index(barcode.format))
            }
        }
    }

    private suspend fun mapJS(
        service: JsEngineService.LocalBinder,
        value: String,
        format: String
    ): String {
        return service.evaluateTemplate(
            _jsCode ?: return value,
            value,
            format,
        )
    }

    suspend fun tapToFocus(tapCoords: Offset) {
        surfaceMeteringPointFactory?.createPoint(tapCoords.x, tapCoords.y)?.let {
            val meteringAction = FocusMeteringAction.Builder(it).build()
            suspendCoroutine {
                cameraControl?.startFocusAndMetering(meteringAction)?.addListener({
                    it.resume(Unit)
                }, Executors.newSingleThreadExecutor()) ?: it.resume(Unit)
            }
        }
    }
}