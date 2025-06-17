package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.LocalJsEngineService
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getMultiPreferenceState
import dev.fabik.bluetoothhid.utils.getPreferenceStateBlocking
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.totschnig.ocr.Line
import org.totschnig.ocr.Text
import zxingcpp.BarcodeReader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraPreviewContent(
    viewModel: CameraViewModel = viewModel<CameraViewModel>(),
    onCameraReady: (CameraControl?, CameraInfo?, ImageCapture?) -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    val scope = rememberCoroutineScope()
    val errorDialog = rememberDialogState()

    // Camera settings
    val camera by context.getMultiPreferenceState(
        PreferenceStore.FRONT_CAMERA,
        PreferenceStore.SCAN_RESOLUTION,
        PreferenceStore.FIX_EXPOSURE,
        PreferenceStore.FOCUS_MODE
    )

    var isPaused by rememberSaveable { mutableStateOf(false) }

    ComposableLifecycle(lifecycleOwner) { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> isPaused = false
            Lifecycle.Event.ON_PAUSE -> isPaused = true
            else -> {}
        }
    }

    Log.d("CameraViewModel", "Recompose $isPaused $camera")

    if (!isPaused && camera != null) {
        LaunchedEffect(configuration, lifecycleOwner, camera) {
            runCatching {
                camera?.let {
                    viewModel.bindToCamera(
                        context.applicationContext,
                        lifecycleOwner,
                        PreferenceStore.FRONT_CAMERA.extract(it),
                        PreferenceStore.SCAN_RESOLUTION.extract(it),
                        PreferenceStore.FIX_EXPOSURE.extract(it),
                        PreferenceStore.FOCUS_MODE.extract(it),
                        onCameraReady = onCameraReady,
                        onBarcode = onBarcodeDetected,
                    )
                }
            }.onFailure {
                Log.e("CameraPreview", "Error binding camera!", it)
                errorDialog.open()
            }
        }
    }

    CameraPreviewPreferences(viewModel)

    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    surfaceRequest?.let { request ->
        var isFocusing by remember { mutableStateOf(false) }
        var autofocusCoords by remember { mutableStateOf(Offset.Unspecified) }

        val previewMode by context.getPreferenceStateBlocking(PreferenceStore.PREVIEW_PERFORMANCE_MODE)

        val coordinateTransformer = remember { MutableCoordinateTransformer() }

        CameraXViewfinder(surfaceRequest = request,
            coordinateTransformer = coordinateTransformer,
            implementationMode = if (previewMode) ImplementationMode.EXTERNAL else ImplementationMode.EMBEDDED,
            modifier = Modifier
                .pointerInput(viewModel, coordinateTransformer) {
                    detectTapGestures { tapCoords ->
                        if (!isFocusing) {
                            scope.launch {
                                autofocusCoords = tapCoords
                                isFocusing = true
                                with(coordinateTransformer) {
                                    viewModel.tapToFocus(tapCoords.transform())
                                }
                                isFocusing = false
                            }
                        }
                    }
                }
                .pointerInput(viewModel) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.pinchToZoom(zoom)
                    }
                })

        OverlayCanvas(viewModel)

        AnimatedVisibility(visible = isFocusing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset {
                    autofocusCoords
                        .takeOrElse { Offset.Zero }
                        .round()
                }
                .offset((-24).dp, (-24).dp)) {
            Spacer(
                Modifier
                    .border(2.dp, Color.White, CircleShape)
                    .size(48.dp)
            )
        }
    }

    OcrDetectionFAB(viewModel)

    InfoDialog(dialogState = errorDialog, title = stringResource(R.string.camera_error), icon = {
        Icon(
            Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error
        )
    }) {
        Text(stringResource(R.string.camera_error_desc))
    }
}

@Composable
fun CameraPreviewPreferences(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val jsEngineService = LocalJsEngineService.current

    // Scanner settings
    val scanner by context.getMultiPreferenceState(
        PreferenceStore.SCAN_FREQUENCY,
        PreferenceStore.FULL_INSIDE,
        PreferenceStore.SCAN_REGEX,
        PreferenceStore.ENABLE_JS,
        PreferenceStore.JS_CODE
    )

    scanner?.let {
        LaunchedEffect(scanner, jsEngineService) {
            val jsEnabled = PreferenceStore.ENABLE_JS.extract(it)
            val jsCode = PreferenceStore.JS_CODE.extract(it)
            val scanRegex = runCatching {
                val regex = PreferenceStore.SCAN_REGEX.extract(it)
                if (!regex.isBlank()) regex.toRegex() else null
            }.getOrNull()

            viewModel.updateScanParameters(
                PreferenceStore.FULL_INSIDE.extract(it),
                scanRegex,
                if (jsEnabled) jsCode else null,
                PreferenceStore.SCAN_FREQUENCY.extract(it),
                jsEngineService
            )
        }
    }

    // Barcode reader options
    val reader by context.getMultiPreferenceState(
        PreferenceStore.CODE_TYPES,
        PreferenceStore.ADV_TRY_HARDER,
        PreferenceStore.ADV_TRY_ROTATE,
        PreferenceStore.ADV_TRY_INVERT,
        PreferenceStore.ADV_TRY_DOWNSCALE,
        PreferenceStore.ADV_MIN_LINE_COUNT,
        PreferenceStore.ADV_BINARIZER,
        PreferenceStore.ADV_DOWNSCALE_FACTOR,
        PreferenceStore.ADV_DOWNSCALE_THRESHOLD,
        PreferenceStore.ADV_TEXT_MODE
    )

    reader?.let {
        LaunchedEffect(reader) {
            viewModel.updateBarcodeReaderOptions(
                PreferenceStore.CODE_TYPES.extract(it),
                PreferenceStore.ADV_TRY_HARDER.extract(it),
                PreferenceStore.ADV_TRY_ROTATE.extract(it),
                PreferenceStore.ADV_TRY_INVERT.extract(it),
                PreferenceStore.ADV_TRY_DOWNSCALE.extract(it),
                PreferenceStore.ADV_MIN_LINE_COUNT.extract(it),
                PreferenceStore.ADV_BINARIZER.extract(it),
                PreferenceStore.ADV_DOWNSCALE_FACTOR.extract(it),
                PreferenceStore.ADV_DOWNSCALE_THRESHOLD.extract(it),
                PreferenceStore.ADV_TEXT_MODE.extract(it),
            )
        }
    }
}

@Composable
private fun OcrDetectionFAB(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val resultDialog = rememberDialogState()

    var imageSize by remember { mutableStateOf(Size(0, 0)) }
    var results by remember { mutableStateOf<List<Line>>(emptyList()) }

    val startForResult =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("Scanner", "Activity result $result")

            if (result.resultCode == Activity.RESULT_OK) {
                val text: Text? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra("result", Text::class.java)
                } else {
                    result.data?.getParcelableExtra("result")
                }

                text?.let {
                    Log.d("Scanner", "Scan result: $it $imageSize")

                    results = it.textBlocks.flatMap { block ->
                        block.lines.filter { line ->
                            line.boundingBox?.let { bb ->
                                val cornerPoints = listOf(
                                    Point(bb.left, bb.top),
                                    Point(bb.right, bb.top),
                                    Point(bb.right, bb.bottom),
                                    Point(bb.left, bb.bottom),
                                ).map {
                                    viewModel.transformPoint(it, imageSize)
                                }

                                Log.d("Scanner", "Line: ${line.text} $cornerPoints")

                                if (cornerPoints.any {
                                        viewModel.scanRect.contains(
                                            Offset(
                                                it.x, it.y
                                            )
                                        )
                                    }) {
                                    return@filter true
                                }
                            }

                            false
                        }
                    }

                    if (results.size > 1) {
                        resultDialog.open()
                    } else if (results.isNotEmpty()) {
                        viewModel.onBarcodeDetected(results.first().text, BarcodeReader.Format.NONE)
                    }
                } ?: {
                    Log.d("Scanner", "No result")
                }
            }
        }

    InfoDialog(resultDialog, "Multiple results") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(results) {
                ElevatedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.onBarcodeDetected(it.text, BarcodeReader.Format.NONE)
                            viewModel._currentBarcode.update { _ ->
                                val bb = it.boundingBox!!
                                val cornerPoints = listOf(
                                    Point(bb.left, bb.top),
                                    Point(bb.right, bb.top),
                                    Point(bb.right, bb.bottom),
                                    Point(bb.left, bb.bottom),
                                ).map {
                                    viewModel.transformPoint(it, imageSize)
                                }
                                CameraViewModel.Barcode(
                                    it.text, cornerPoints, Size(0, 0), BarcodeReader.Format.NONE
                                )
                            }
                            resultDialog.close()
                        }) {
                    Text(it.text, Modifier.padding(4.dp))
                }
            }
        }
    }

    FloatingActionButton(onClick = {
        viewModel.imageCapture?.takePicture(
            Executors.newSingleThreadExecutor(),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("Scanner", "Capture failed!", exc)
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

                        imageSize = Size(bitmap.width, bitmap.height)

                        image.close()

                        FileOutputStream(file).use { output ->
                            output.write(bytes)
                        }
                    } else {
                        image.close()
                        return
                    }

                    val photoUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", file
                    )

                    // For debugging use "android.intent.action.VIEW"
                    val intent = Intent("org.totschnig.ocr.action.RECOGNIZE").apply {
                        setDataAndType(photoUri, "image/jpeg")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    runCatching {
                        Log.d("Scanner", "Launching intent with $photoUri $imageSize")
                        startForResult.launch(intent)
                    }.onFailure {
                        Log.e("Scanner", "Unable start intent!", it)
                    }
                }
            })
    }, modifier = Modifier) {
        Icon(Icons.Default.DocumentScanner, null)
    }
}