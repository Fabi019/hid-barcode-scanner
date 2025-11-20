package dev.fabik.bluetoothhid.ui.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.graphics.YuvImage
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.Size
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
import dev.fabik.bluetoothhid.utils.ImageUtils
import dev.fabik.bluetoothhid.utils.JsEngineService
import dev.fabik.bluetoothhid.utils.LatencyTrace
import dev.fabik.bluetoothhid.utils.ScanFrequency
import dev.fabik.bluetoothhid.utils.ScanResolution
import dev.fabik.bluetoothhid.utils.TextMode
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.totschnig.ocr.Line
import org.totschnig.ocr.TextBlock
import zxingcpp.BarcodeReader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
class CameraViewModel : ViewModel() {
    companion object {
        const val TAG = "CameraViewModel"

        val SD_480P = Size(640, 480)
        val HD_720P = Size(960, 720)
        val FHD_1080P = Size(1440, 1080)
        val UHD_2160P = Size(2160, 1440)
    }

    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private var surfaceMeteringPointFactory: SurfaceOrientedMeteringPointFactory? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    var imageCapture: ImageCapture? = null
    private var barcodeAnalyzer: ZXingAnalyzer? = null

    var onBarcodeDetected: (String, BarcodeReader.Format, ImageProxy?) -> Unit = { _, _, _ -> }

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

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        frontCamera: Boolean,
        resolution: ScanResolution,
        fixExposure: Boolean,
        focusMode: FocusMode,
        onCameraReady: (CameraControl?, CameraInfo?, ImageCapture?) -> Unit,
        onBarcode: (String, Int, String?) -> Unit,
    ) {
        Log.d(TAG, "Binding camera...")
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        onBarcodeDetected = { value, format, image ->
            if (!value.contentEquals(lastBarcode)) {
                Log.d(TAG, "New barcode detected: $value")

                val formatIdx = ZXingAnalyzer.format2Index(format)
                HistoryViewModel.addHistoryItem(value, formatIdx)

                val scanImageName = _saveScanPath?.let { path ->
                    image?.let { image ->
                        saveScanImage(appContext, image, path)
                    }
                }

                viewModelScope.launch {
                    onBarcode(value, formatIdx, scanImageName)
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
                when (resolution) {
                    ScanResolution.UHD_2160P -> UHD_2160P
                    ScanResolution.FHD_1080P -> FHD_1080P
                    ScanResolution.HD_720P -> HD_720P
                    ScanResolution.SD_480P -> SD_480P
                },
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        ).build()

        val analyzerBuilder = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageRotationEnabled(true)
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

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(cameraPreviewUseCase)
            .addUseCase(analysis)
            .addUseCase(imageCapture!!)
            .build()

        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner,
            if (frontCamera && processCameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup
        )

        cameraControl = camera.cameraControl
        cameraInfo = camera.cameraInfo

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

    fun onBarcodeAnalyze() {
        cameraTrace.trigger()
    }

    var viewSize: IntSize? = null
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
        _readerOptions.minLineCount = minLines
        _readerOptions.binarizer = when (binarizer) {
            Binarizer.LOCAL_AVERAGE -> BarcodeReader.Binarizer.LOCAL_AVERAGE
            Binarizer.GLOBAL_HISTOGRAM -> BarcodeReader.Binarizer.GLOBAL_HISTOGRAM
            Binarizer.FIXED_THRESHOLD -> BarcodeReader.Binarizer.FIXED_THRESHOLD
            Binarizer.BOOL_CAST -> BarcodeReader.Binarizer.BOOL_CAST
        }
        _readerOptions.downscaleFactor = downscaleFactor
        _readerOptions.downscaleThreshold = downscaleThreshold
        _readerOptions.textMode = when (textMode) {
            TextMode.PLAIN -> BarcodeReader.TextMode.PLAIN
            TextMode.ECI -> BarcodeReader.TextMode.ECI
            TextMode.HRI -> BarcodeReader.TextMode.HRI
            TextMode.HEX -> BarcodeReader.TextMode.HEX
            TextMode.ESCAPED -> BarcodeReader.TextMode.ESCAPED
        }

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

    fun updateScanParameters(
        fullyInside: Boolean,
        scanRegex: Regex?,
        jsCode: String?,
        frequency: ScanFrequency,
        jsEngineService: JsEngineService.LocalBinder?,
        saveScanPath: String?,
        saveScanCropMode: CropMode,
        scanSaveQuality: Int,
        saveScanFileName: String
    ) {
        _scanDelay = when (frequency) {
            ScanFrequency.FASTEST -> 0
            ScanFrequency.FAST -> 100
            ScanFrequency.NORMAL -> 500
            ScanFrequency.SLOW -> 1000
        }
        barcodeAnalyzer?.scanDelay = _scanDelay
        _fullyInside = fullyInside
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

        Log.d(TAG, "Updated scan parameters")
    }

    var lastBarcode: String? = null
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
        sourceImage: ImageProxy
    ) {
        detectorTrace.trigger()

        val source = Size(sourceImage.width, sourceImage.height)
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

        barcode?.value?.let { v ->
            var value = v

            _scanRegex?.let { re ->
                // extract first capture group if it exists
                re.find(value)?.let { match ->
                    match.groupValues.getOrNull(1)?.let { group ->
                        value = group
                    }
                }
            }

            _jsEngineService?.let { s ->
                // Only blocks the analyzer thread
                runBlocking {
                    value =
                        mapJS(s, value, ZXingAnalyzer.format2String(barcode.format))
                    onBarcodeDetected(value, barcode.format, sourceImage)
                }
            } ?: run {
                onBarcodeDetected(value, barcode.format, sourceImage)
            }
        }
    }

    private val _ocrResults = MutableStateFlow<List<Line>>(emptyList())
    val ocrResults = _ocrResults.asStateFlow()

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

                    return@filter when (_fullyInside) {
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
            onBarcodeDetected(results.first().text, BarcodeReader.Format.NONE, null)
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
                        val matrix =
                            Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                        bitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )

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

    fun saveScanImage(context: Context, image: ImageProxy, path: Uri): String? {
        val yuvImage =
            YuvImage(
                ImageUtils.yuv420888toNv21(image),
                ImageFormat.NV21,
                image.width,
                image.height,
                null
            )

        val barcode = currentBarcode.value ?: return null

        val rect = when (_saveScanCropMode) {
            CropMode.NONE -> android.graphics.Rect(0, 0, image.width, image.height)
            CropMode.SCAN_AREA, CropMode.BARCODE -> {
                var (topLeft, bottomRight) = if (_saveScanCropMode == CropMode.SCAN_AREA) {
                    scanRect.topLeft.let { Point(it.x.toInt(), it.y.toInt()) } to
                            scanRect.bottomRight.let { Point(it.x.toInt(), it.y.toInt()) }
                } else {
                    val minX = barcode.cornerPoints.minOf { it.x } - 5
                    val maxX = barcode.cornerPoints.maxOf { it.x } + 5
                    val minY = barcode.cornerPoints.minOf { it.y } - 5
                    val maxY = barcode.cornerPoints.maxOf { it.y } + 5

                    Point(max(0f, minX).toInt(), max(0f, minY).toInt()) to
                            Point(
                                min(maxX, image.width.toFloat()).toInt(),
                                min(maxY, image.height.toFloat()).toInt()
                            )
                }

                val size = Size(image.width, image.height)
                topLeft = transformPoint(topLeft, size, true).toPoint()
                bottomRight = transformPoint(bottomRight, size, true).toPoint()

                android.graphics.Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
            }
        }

        // Determine filename
        var fileName = "${_saveScanFileName}.jpg"
        fileName = fileName.replace("{TIMESTAMP}", System.currentTimeMillis().toString())
        fileName = fileName.replace(
            "{FORMAT}",
            ZXingAnalyzer.format2String(barcode.format)
        )
        fileName = fileName.replace("{CODE}", barcode.value?.replace("/", "") ?: "")

        val newFileUri = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                path,
                "image/jpeg",
                fileName
            )
        }.getOrNull()

        newFileUri?.let { file ->
            context.contentResolver.openOutputStream(file).use {
                if (yuvImage.compressToJpeg(rect, _saveScanQuality, it)) {
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
        return service.evaluateTemplate(
            _jsCode ?: return value,
            value,
            format,
        ) ?: value
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

    fun pinchToZoom(zoom: Float) {
        val currentZoom = cameraInfo?.zoomState?.value ?: return
        val currentZoomRatio = currentZoom.zoomRatio
        val newZoomRatio = (currentZoomRatio * zoom).coerceIn(
            currentZoom.minZoomRatio,
            currentZoom.maxZoomRatio
        )
        cameraControl?.setZoomRatio(newZoomRatio)
    }

}