package dev.fabik.bluetoothhid.ui.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.featuregroup.GroupableFeature.Companion.FPS_60
import androidx.camera.core.featuregroup.GroupableFeature.Companion.PREVIEW_STABILIZATION
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
import androidx.core.content.FileProvider
import androidx.core.graphics.toPoint
import androidx.core.graphics.toPointF
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fabik.bluetoothhid.utils.Binarizer
import dev.fabik.bluetoothhid.utils.CropMode
import dev.fabik.bluetoothhid.utils.FocusMode
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.LatencyTrace
import dev.fabik.bluetoothhid.utils.ScanAreaData
import dev.fabik.bluetoothhid.utils.ScanAreaData.Companion.toOverlayOffset
import dev.fabik.bluetoothhid.utils.ScanAreaData.Companion.toOverlaySize
import dev.fabik.bluetoothhid.utils.ScanFrequency
import dev.fabik.bluetoothhid.utils.ScanImageFormat
import dev.fabik.bluetoothhid.utils.ScanResolution
import dev.fabik.bluetoothhid.utils.TextMode
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.totschnig.ocr.Line
import org.totschnig.ocr.TextBlock
import zxingcpp.BarcodeReader
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
class CameraViewModel : ViewModel() {
    companion object {
        const val TAG = "CameraViewModel"
    }

    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    // Emits error messages when JavaScript evaluation fails
    private val _jsErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val jsErrors: SharedFlow<String> = _jsErrors.asSharedFlow()

    private var surfaceMeteringPointFactory: SurfaceOrientedMeteringPointFactory? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    var imageCapture: ImageCapture? = null
    private var barcodeAnalyzer: ZXingAnalyzer? = null

    // Internal callback: carries the processed value, format, image and regex capture groups.
    // regexGroups contains groups 1..N from the filter regex (empty if no regex or no groups).
    var onBarcodeDetected: (String?, BarcodeReader.Format, ImageProxy?, List<String>) -> Unit =
        { _, _, _, _ -> }

    var scanRect = Rect.Zero
    // For CUSTOM overlay with multiple areas: individual rects for filtering, empty = use scanRect
    var scanRects: List<Rect> = emptyList()
    private val _areas = MutableStateFlow<List<ScanAreaData>>(emptyList())
    val areas = _areas.asStateFlow()
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

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        frontCamera: Boolean,
        resolution: ScanResolution,
        fixExposure: Boolean,
        focusMode: FocusMode,
        perfMode: Boolean,
        stabilization: Boolean,
        initialZoom: Float,
        onCameraReady: (CameraControl?, CameraInfo?, ImageCapture?) -> Unit,
        onBarcode: (BarcodeResult?) -> Unit,
    ) {
        Log.d(TAG, "Binding camera...")
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        onBarcodeDetected = { value, format, image, regexGroups ->
            lastDetectionTime = System.currentTimeMillis()
            if (value == null) {
                if (lastBarcode != null) {
                    Log.d(TAG, "Clearing barcode value")
                    viewModelScope.launch {
                        onBarcode(null)
                    }
                    lastBarcode = null
                }
            } else if (!value.contentEquals(lastBarcode)) {
                Log.d(TAG, "New barcode detected: $value")

                val formatIdx = ZXingAnalyzer.format2Index(format)
                HistoryViewModel.addHistoryItem(value, formatIdx)

                val scanImageName = _saveScanPath?.let { path ->
                    image?.let { image ->
                        runCatching {
                            saveScanImage(appContext, image, path)
                        }.onFailure {
                            Log.e(TAG, "Error saving scan!", it)
                            viewModelScope.launch {
                                Toast.makeText(
                                    appContext,
                                    "Error saving scan image!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }.getOrNull()
                    }
                }

                viewModelScope.launch {
                    onBarcode(BarcodeResult(value, formatIdx, scanImageName, regexGroups))
                }
                lastBarcode = value
            }
        }

        val analyzer = ZXingAnalyzer(
            _readerOptions,
            _scanDelay,
            ::onBarcodeAnalyze,
            ::onBarcodeResult
        )
        barcodeAnalyzer = analyzer

        val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
                resolution.size,
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        ).build()

        val analyzerBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageRotationEnabled(false)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

        // Apply focus mode settings
        Camera2Interop.Extender(analyzerBuilder).apply {
            //setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
            //setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE)

            if (fixExposure) {
                // Sets a fixed exposure compensation and iso for the image
                setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 1600)
                setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -8)
            }

            if (focusMode != FocusMode.AUTO) {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE, when (focusMode) {
                        FocusMode.MANUAL -> CaptureRequest.CONTROL_AF_MODE_AUTO
                        FocusMode.MACRO -> CaptureRequest.CONTROL_AF_MODE_MACRO
                        FocusMode.CONTINUOUS -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        FocusMode.EDOF -> CaptureRequest.CONTROL_AF_MODE_EDOF
                        FocusMode.INFINITY -> CaptureRequest.CONTROL_AF_MODE_OFF
                        FocusMode.CONTINUOUS_PICTURE -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        else -> CaptureRequest.CONTROL_AF_MODE_OFF
                    }
                )

                if (focusMode == FocusMode.INFINITY) {
                    setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
                }
            }
        }

        val analysis = analyzerBuilder.build()
        val executor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor, analyzer)

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner,
            if (frontCamera && processCameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
            SessionConfig(
                useCases = listOf(cameraPreviewUseCase, analysis, imageCapture!!),
                preferredFeatureGroup = buildList {
                    if (perfMode) add(FPS_60)
                    if (stabilization) add(PREVIEW_STABILIZATION)
                },
            )
        )

        cameraControl = camera.cameraControl
        cameraInfo = camera.cameraInfo

        camera.cameraInfo.zoomState.value?.let { zoomState ->
            camera.cameraControl.setZoomRatio(
                initialZoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
            )
        }

        onCameraReady(camera.cameraControl, camera.cameraInfo, imageCapture)
        Log.d(TAG, "Camera is ready!")

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } catch (_: CancellationException) {
            Log.d(TAG, "Coroutine was cancelled")
        } finally {
            Log.d(TAG, "Unbinding camera...")
            processCameraProvider.unbindAll()
            if (!executor.isShutdown)
                executor.shutdown()
            cameraControl = null
            cameraInfo = null
            imageCapture = null
            barcodeAnalyzer = null
            onCameraReady(null, null, null)
        }
    }

    fun onBarcodeAnalyze(source: Size, rotation: Int) {
        cameraTrace.trigger()

        if (_fullyInside && barcodeAnalyzer?.currentScanRect != scanRect) {
            val topLeft = transformPoint(scanRect.topLeft.toPoint(), source, true).toPoint()
            val bottomRight = transformPoint(scanRect.bottomRight.toPoint(), source, true).toPoint()
            val rect = android.graphics.Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
            barcodeAnalyzer?.cropRect = when (rotation) {
                90 -> android.graphics.Rect(
                    rect.top,
                    source.width - rect.right,
                    rect.bottom,
                    source.width - rect.left
                )

                180 -> android.graphics.Rect(
                    source.width - rect.right,
                    source.height - rect.bottom,
                    source.width - rect.left,
                    source.height - rect.top
                )

                270 -> android.graphics.Rect(
                    source.height - rect.bottom,
                    rect.left,
                    source.height - rect.top,
                    rect.right
                )

                else -> rect
            }
            barcodeAnalyzer?.currentScanRect = scanRect
        }

        _clearAfterTime?.let {
            val now = System.currentTimeMillis()
            if (now - (lastDetectionTime ?: now) >= it) {
                _currentBarcode.update { null }
                onBarcodeDetected(null, BarcodeReader.Format.NONE, null, emptyList())
                lastDetectionTime = null
            }
        }
    }

    var viewSize by mutableStateOf<IntSize?>(null)
    var lastScanSize: Size? = null
    private var scale = 1.0f
    private var translation = Offset(0.0f, 0.0f)

    fun transformPoint(point: Point, scanSize: Size, reverse: Boolean = false): PointF {
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

        return if (reverse) {
            PointF((point.x + translation.x) / scale, (point.y + translation.y) / scale)
        } else {
            PointF(point.x * scale - translation.x, point.y * scale - translation.y)
        }
    }

    private var _readerOptions = BarcodeReader.Options()

    fun updateBarcodeReaderOptions(
        codeTypes: Set<String>,
        tryHarder: Boolean,
        tryRotate: Boolean,
        tryInvert: Boolean,
        tryDownscale: Boolean,
        tryDenoise: Boolean,
        minLines: Int,
        binarizer: Binarizer,
        downscaleFactor: Int,
        downscaleThreshold: Int,
        textMode: TextMode
    ) {
        _readerOptions.formats = codeTypes.mapNotNull { it.toIntOrNull() }
            .map { ZXingAnalyzer.index2Format(it) }.toSet()
        _readerOptions.tryHarder = tryHarder
        _readerOptions.tryRotate = tryRotate
        _readerOptions.tryInvert = tryInvert
        _readerOptions.tryDownscale = tryDownscale
        _readerOptions.tryDenoise = tryDenoise
        _readerOptions.minLineCount = minLines
        _readerOptions.binarizer = binarizer.readerValue
        _readerOptions.downscaleFactor = downscaleFactor
        _readerOptions.downscaleThreshold = downscaleThreshold
        _readerOptions.textMode = textMode.readerValue

        Log.d(TAG, "Updating reader options: $_readerOptions")
        barcodeAnalyzer?.setOptions(_readerOptions)
    }

    private var _fullyInside: Boolean = false
    private var _scanRegex: Regex? = null
    private var _jsCode: String? = null
    private var _scanDelay: Int = 0
    private var _jsEngineService: JsEngineService.LocalBinder? = null
    private var _saveScanPath: Uri? = null
    private var _saveScanCropMode: CropMode = CropMode.NONE
    private var _saveScanQuality: Int = 100
    private var _saveScanFileName: String = "scan"
    private var _clearAfterTime: Long? = null
    private var _saveScanImageFormat: ScanImageFormat = ScanImageFormat.JPEG

    fun updateScanParameters(
        fullyInside: Boolean,
        scanRegex: Regex?,
        jsCode: String?,
        frequency: ScanFrequency,
        jsEngineService: JsEngineService.LocalBinder?,
        saveScanPath: String?,
        saveScanCropMode: CropMode,
        scanSaveQuality: Int,
        saveScanFileName: String,
        clearAfterTime: Long?,
        saveScanImageFormat: ScanImageFormat,
        multiCodeDetection: Boolean = false,
        autoSend: Boolean = false
    ) {
        _scanDelay = frequency.delayMs
        barcodeAnalyzer?.scanDelay = _scanDelay
        _fullyInside = fullyInside
        if (!fullyInside) {
            barcodeAnalyzer?.cropRect = null
            barcodeAnalyzer?.currentScanRect = null
        }

        _scanRegex = scanRegex
        _jsCode = jsCode
        _jsEngineService = jsEngineService

        _saveScanPath = saveScanPath?.let {
            val pathUri = it.toUri()
            // Get the document ID for the directory
            val docId = DocumentsContract.getTreeDocumentId(pathUri)
            DocumentsContract.buildDocumentUriUsingTree(pathUri, docId)
        }

        _saveScanCropMode = saveScanCropMode
        _saveScanQuality = scanSaveQuality
        _saveScanFileName = saveScanFileName
        _saveScanImageFormat = saveScanImageFormat

        _clearAfterTime = clearAfterTime
        this.multiCodeDetection = multiCodeDetection
        _autoSend = autoSend

        Log.d(TAG, "Updated scan parameters")
    }

    // Called when the user manually selects a barcode from the multi-code picker.
    fun selectBarcode(barcode: Barcode) {
        processAndDispatch(barcode, sourceImage = null, resetDedup = true)
    }

    // Applies regex extraction and JS mapping, then calls onBarcodeDetected.
    // useRunBlocking=true is needed on the analyzer thread (auto-send) to preserve frame ordering.
    // resetDedup=true clears lastBarcode so the same value can be re-sent (used by selectBarcode and auto-send).
    private fun processAndDispatch(
        barcode: Barcode,
        sourceImage: ImageProxy?,
        resetDedup: Boolean = false
    ) {
        if (resetDedup) lastBarcode = null
        _currentBarcode.update { barcode }
        val rawValue = barcode.value ?: return
        var value = rawValue

        val regexGroups: List<String> = _scanRegex?.let { re ->
            re.find(value)?.let { match ->
                match.groupValues.getOrNull(1)?.let { group -> value = group }
                match.groupValues.drop(1)
            } ?: emptyList()
        } ?: emptyList()

        _jsEngineService?.let { s ->
            // This must be blocking otherwise the image might already be closed
            // when trying to save it later in onBarcodeDetected
            value = runBlocking {
                mapJS(s, value, ZXingAnalyzer.format2String(barcode.format))
            }
        }
        onBarcodeDetected(value, barcode.format, sourceImage, regexGroups)
    }

    var lastDetectionTime: Long? = null
    var lastBarcode: String? = null
    private val _currentBarcode = MutableStateFlow<Barcode?>(null)
    val currentBarcode: StateFlow<Barcode?> = _currentBarcode.asStateFlow()

    // All barcodes detected in the current frame (multi-code mode visual)
    private val _detectedBarcodes = MutableStateFlow<List<Barcode>>(emptyList())
    val detectedBarcodes: StateFlow<List<Barcode>> = _detectedBarcodes.asStateFlow()

    // Tracks which barcodes have already been queued in the current "scene"
    // Cleared when the frame contains no barcodes (camera moved away)
    private val sentInSession = mutableSetOf<String>()

    var multiCodeDetection: Boolean = false
    private var _autoSend: Boolean = false

    data class Barcode(
        var value: String?,
        val cornerPoints: List<PointF>,
        val format: BarcodeReader.Format
    )

    fun onBarcodeResult(
        result: List<BarcodeReader.Result>,
        sourceImage: ImageProxy,
        source: Size
    ) {
        detectorTrace.trigger()

        val allBarcodes = result.map {
            val cornerPoints = listOf(
                it.position.topLeft,
                it.position.topRight,
                it.position.bottomRight,
                it.position.bottomLeft
            ).map { p ->
                if (_fullyInside) {
                    // Add offset from crop rect onto the position
                    val cropRect = barcodeAnalyzer?.cropRect ?: android.graphics.Rect()
                    when (sourceImage.imageInfo.rotationDegrees) {
                        0 -> {
                            p.x += cropRect.left
                            p.y += cropRect.top
                        }

                        90 -> {
                            p.y += cropRect.left
                            p.x += source.width - cropRect.bottom
                        }

                        180 -> {
                            p.x += source.width - cropRect.right
                            p.y += source.height - cropRect.bottom
                        }

                        270 -> {
                            p.y += source.height - cropRect.right
                            p.x += cropRect.top
                        }
                    }
                }
                transformPoint(p, source)
            }

            Barcode(it.text, cornerPoints, it.format)
        }.filter {
            if (scanRects.size > 1) {
                // Multi-area: check each individual rect
                if (_fullyInside)
                    scanRects.any { r -> it.cornerPoints.all { p -> r.contains(Offset(p.x, p.y)) } }
                else
                    it.cornerPoints.any { p -> scanRects.any { r -> r.contains(Offset(p.x, p.y)) } }
            } else {
                when (_fullyInside) {
                    true -> true    // Automatically handled with setting cropRect
                    false -> it.cornerPoints.any { scanRect.contains(Offset(it.x, it.y)) }
                }
            }
        }.filter {
            _scanRegex?.matches(it.value ?: return@filter true) != false
        }

        // Always expose all detected barcodes for visual overlay and picker
        _detectedBarcodes.update { allBarcodes }

        if (_autoSend) {
            // Auto-send: queue all new barcodes in this frame (dedup within session)
            if (allBarcodes.isEmpty()) {
                sentInSession.clear()
                if (lastBarcode != null) {
                    _currentBarcode.update { null }
                    onBarcodeDetected(null, BarcodeReader.Format.NONE, null, emptyList())
                    lastBarcode = null
                }
                return
            }

            val newBarcodes = allBarcodes.filter { !sentInSession.contains(it.value) }
            newBarcodes.forEach { barcode ->
                if (barcode.value != null) {
                    sentInSession.add(barcode.value!!)
                    processAndDispatch(
                        barcode,
                        sourceImage,
                        resetDedup = true
                    )
                }
            }
        } else if (multiCodeDetection) {
            // Manual multi-code: barcodes are exposed via _detectedBarcodes for the picker UI.
            // No auto-send — user selects from the picker. Clear session when frame is empty.
            if (allBarcodes.isEmpty()) {
                sentInSession.clear()
                _currentBarcode.update { null }
            }
        } else {
            // Single-code mode: existing behaviour, take first match
            val barcode = allBarcodes.firstOrNull()
            if (barcode != null) {
                processAndDispatch(barcode, sourceImage)
            } else {
                _currentBarcode.update { null }
            }
        }
    }

    private val _ocrResults = MutableStateFlow<List<Line>>(emptyList())
    val ocrResults = _ocrResults.asStateFlow()
    private val _triggerOcr = MutableStateFlow<Boolean?>(null)
    val triggerOcr = _triggerOcr.asStateFlow()

    fun triggerOcr() {
        _triggerOcr.update { !(it ?: false) }
    }

    fun onOcrResult(result: List<TextBlock>, source: Size): Boolean {
        val results = result.flatMap { block ->
            block.lines.filter { line ->
                line.boundingBox?.let { bb ->
                    val cornerPoints = listOf(
                        Point(bb.left, bb.top),
                        Point(bb.right, bb.top),
                        Point(bb.right, bb.bottom),
                        Point(bb.left, bb.bottom),
                    ).map {
                        val point = transformPoint(it, source)
                        Offset(point.x, point.y)
                    }

                    return@filter if (scanRects.size > 1) {
                        if (_fullyInside)
                            scanRects.any { r -> cornerPoints.all { p -> r.contains(p) } }
                        else
                            scanRects.any { r -> r.overlaps(Rect(cornerPoints[0], cornerPoints[2])) }
                    } else when (_fullyInside) {
                        false -> scanRect.overlaps(Rect(cornerPoints[0], cornerPoints[2]))
                        true -> cornerPoints.all { scanRect.contains(it) }
                    }
                }

                false
            }
        }

        Log.d(TAG, "OCR results: $results")

        if (results.size > 1) {
            _ocrResults.update { _ -> results }
            return true
        } else if (results.isNotEmpty()) {
            onBarcodeDetected(results.first().text, BarcodeReader.Format.NONE, null, emptyList())
        }

        return false
    }

    fun captureImageOCR(context: Context, onSuccess: (Uri, Size) -> Unit) {
        imageCapture?.takePicture(
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Capture failed!", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    // Override previous capture
                    val file = File(context.cacheDir, "capture.jpg")

                    if (image.format == ImageFormat.JPEG || image.format == ImageFormat.JPEG_R) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        // Rotate image based on rotationDegrees
                        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (image.imageInfo.rotationDegrees != 0) {
                            bitmap = Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },
                                true
                            )
                        }

                        // Write image into temporary file
                        FileOutputStream(file).use { output ->
                            output.write(bytes)
                        }

                        val photoUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )

                        onSuccess(photoUri, Size(bitmap.width, bitmap.height))
                    } else {
                        Log.w(TAG, "Unsupported capture format: ${image.format}")
                    }

                    image.close()
                }
            }
        )
    }

    @SuppressLint("RestrictedApi")
    fun saveScanImage(context: Context, image: ImageProxy, path: Uri): String? {
        var bitmap = image.toBitmap()
        if (image.imageInfo.rotationDegrees != 0) {
            bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                image.width,
                image.height,
                Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) },
                true
            )
        }

        val barcode = currentBarcode.value ?: return null

        if (_saveScanCropMode == CropMode.SCAN_AREA || _saveScanCropMode == CropMode.BARCODE) {
            var (topLeft, bottomRight) = if (_saveScanCropMode == CropMode.SCAN_AREA) {
                scanRect.topLeft.toPoint() to scanRect.bottomRight.toPoint()
            } else {
                val minX = barcode.cornerPoints.minOf { it.x } - 5
                val maxX = barcode.cornerPoints.maxOf { it.x } + 5
                val minY = barcode.cornerPoints.minOf { it.y } - 5
                val maxY = barcode.cornerPoints.maxOf { it.y } + 5

                Point(max(0f, minX).toInt(), max(0f, minY).toInt()) to
                        Point(
                            min(maxX, bitmap.width.toFloat()).toInt(),
                            min(maxY, bitmap.height.toFloat()).toInt()
                        )
            }

            val size = Size(bitmap.width, bitmap.height)
            topLeft = transformPoint(topLeft, size, true).toPoint()
            bottomRight = transformPoint(bottomRight, size, true).toPoint()

            // crop image
            bitmap = Bitmap.createBitmap(
                bitmap,
                topLeft.x,
                topLeft.y,
                bottomRight.x - topLeft.x,
                bottomRight.y - topLeft.y
            )
        }

        val mime = _saveScanImageFormat.mimeType
        val ext = _saveScanImageFormat.extension

        // Determine filename
        var fileName = "${_saveScanFileName}.${ext}"
        fileName = fileName.replace("{TIMESTAMP}", System.currentTimeMillis().toString())
        fileName = fileName.replace(
            "{TIMESTAMP_ISO}",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        )
        fileName = fileName.replace(
            "{FORMAT}",
            ZXingAnalyzer.format2String(barcode.format)
        )
        fileName = fileName.replace("{CODE}", barcode.value?.replace("/", "") ?: "")

        val newFileUri = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                path,
                mime,
                fileName
            )
        }.getOrNull()

        newFileUri?.let { file ->
            context.contentResolver.openOutputStream(file).use {
                if (bitmap.compress(
                        _saveScanImageFormat.toCompressFormat(),
                        _saveScanQuality,
                        it ?: return null
                    )
                ) {
                    Log.d(TAG, "Saved scan image to ${file.path}")
                    return file.lastPathSegment?.substringAfterLast("/")
                }
            }
        }

        Log.e(TAG, "Failed to create file at $path")
        return null
    }

    private suspend fun mapJS(
        service: JsEngineService.LocalBinder,
        value: String,
        format: String
    ): String {
        val result = service.evaluateTemplate(
            _jsCode ?: return value,
            value,
            format,
            onException = { throwable ->
                // Emit the actual exception message for UI display (Snackbar in Scanner).
                // console.log output goes through onOutput (null here) and is not shown as errors.
                _jsErrors.tryEmit(throwable.message ?: "JS evaluation failed")
            }
        )

        return result ?: value
    }

    suspend fun tapToFocus(tapCoords: Offset) {
        Log.d(TAG, "Focusing at $tapCoords")
        surfaceMeteringPointFactory?.createPoint(tapCoords.x, tapCoords.y)?.let {
            val meteringAction = FocusMeteringAction.Builder(it).build()
            suspendCoroutine {
                cameraControl?.startFocusAndMetering(meteringAction)?.addListener({
                    it.resume(Unit)
                }, Executors.newSingleThreadExecutor()) ?: it.resume(Unit)
            }
        }
    }

    fun focusAtCenter() {
        val resolution = surfaceRequest.value?.resolution ?: return
        val centerPoint = surfaceMeteringPointFactory?.createPoint(
            resolution.width / 2f,
            resolution.height / 2f
        ) ?: return
        cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(centerPoint).build())
    }

    fun swipeToZoom(dragDeltaY: Float, screenHeight: Int) {
        val currentZoom = cameraInfo?.zoomState?.value ?: return
        val newLinearZoom = (currentZoom.linearZoom - dragDeltaY / (screenHeight / 2f)).coerceIn(0f, 1f)
        cameraControl?.setLinearZoom(newLinearZoom)
    }

    fun pinchToZoom(zoom: Float) {
        val currentZoom = cameraInfo?.zoomState?.value ?: return
        val currentZoomRatio = currentZoom.zoomRatio
        val newZoomRatio = (currentZoomRatio * zoom).coerceIn(
            currentZoom.minZoomRatio,
            currentZoom.maxZoomRatio
        )
        cameraControl?.setZoomRatio(newZoomRatio)
    }

    fun Offset.toPoint() = Point(x.toInt(), y.toInt())

    fun clearScanAreas() {
        _areas.update { emptyList() }
    }

    fun addScanAreas(vararg area: ScanAreaData) {
        _areas.update { it + area }
        // Keep legacy fields in sync with first area for debug overlay
        if (areas.value.isNotEmpty()) {
            overlayPosition = areas.value.first().toOverlayOffset()
            overlaySize = areas.value.first().toOverlaySize()
        }
    }

    fun updateScanArea(index: Int, area: ScanAreaData) {
        if (index >= areas.value.size) return
        _areas.update {
            it.toMutableList().also { l -> l[index] = area }
        }
        // Keep legacy fields in sync with first area for debug overlay
        if (index == 0) {
            overlayPosition = areas.value.first().toOverlayOffset()
            overlaySize = areas.value.first().toOverlaySize()
        }
    }

    fun removeScanArea(index: Int) {
        if (index >= areas.value.size) return
        _areas.update {
            it.toMutableList().also { l -> l.removeAt(index) }
        }
        // Keep legacy fields in sync with first area for debug overlay
        if (areas.value.isNotEmpty()) {
            overlayPosition = areas.value.first().toOverlayOffset()
            overlaySize = areas.value.first().toOverlaySize()
        }
    }
}
