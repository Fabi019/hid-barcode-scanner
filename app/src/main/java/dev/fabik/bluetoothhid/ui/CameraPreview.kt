package dev.fabik.bluetoothhid.ui

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
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
import kotlinx.coroutines.launch

@Composable
fun CameraPreviewContent(
    viewModel: CameraViewModel = viewModel<CameraViewModel>(),
    onCameraReady: (CameraControl?, CameraInfo?, ImageCapture?) -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    val errorDialog = rememberDialogState()

    // Camera settings
    val camera by context.getMultiPreferenceState(
        PreferenceStore.FRONT_CAMERA,
        PreferenceStore.SCAN_RESOLUTION,
        PreferenceStore.FIX_EXPOSURE,
        PreferenceStore.FOCUS_MODE
    )

    var isPaused by remember { mutableStateOf(true) }

    ComposableLifecycle(lifecycleOwner) { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> isPaused = false
            Lifecycle.Event.ON_PAUSE -> isPaused = true
            else -> {}
        }
    }

    if (!isPaused && camera != null) {
        LaunchedEffect(lifecycleOwner, camera) {
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

        CameraXViewfinder(
            surfaceRequest = request,
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
                }
        )

        OverlayCanvas(viewModel)

        AnimatedVisibility(
            visible = isFocusing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset {
                    autofocusCoords
                        .takeOrElse { Offset.Zero }
                        .round()
                }
                .offset((-24).dp, (-24).dp)
        ) {
            Spacer(
                Modifier
                    .border(2.dp, Color.White, CircleShape)
                    .size(48.dp)
            )
        }
    }

    InfoDialog(
        dialogState = errorDialog, title = stringResource(R.string.camera_error),
        icon = {
            Icon(
                Icons.Filled.Error, null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    ) {
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