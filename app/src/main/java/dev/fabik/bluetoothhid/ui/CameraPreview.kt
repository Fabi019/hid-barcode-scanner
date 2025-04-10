package dev.fabik.bluetoothhid.ui

import android.util.Log
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.LocalJsEngineService
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceStateDefault
import dev.fabik.bluetoothhid.utils.rememberPreference
import kotlinx.coroutines.launch

@Composable
fun CameraPreviewContent(
    viewModel: CameraViewModel = viewModel<CameraViewModel>(),
    onCameraReady: (CameraControl?, CameraInfo?) -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    val errorDialog = rememberDialogState()

    // Camera settings
    val frontCamera by rememberPreference(PreferenceStore.FRONT_CAMERA)
    val resolution by rememberPreference(PreferenceStore.SCAN_RESOLUTION)
    val fixExposure by rememberPreference(PreferenceStore.FIX_EXPOSURE)
    val focusMode by rememberPreference(PreferenceStore.FOCUS_MODE)

    LaunchedEffect(lifecycleOwner, frontCamera, resolution, fixExposure, focusMode) {
        runCatching {
            viewModel.bindToCamera(
                context.applicationContext,
                lifecycleOwner,
                frontCamera,
                resolution,
                fixExposure,
                focusMode,
                onCameraReady = onCameraReady,
                onBarcode = onBarcodeDetected,
            )
        }.onFailure {
            Log.e("CameraPreview", "Error binding camera!", it)
            errorDialog.open()
        }
    }

    CameraPreviewPreferences(viewModel)

    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    surfaceRequest?.let { request ->
        var isFocusing by remember { mutableStateOf(false) }
        var autofocusCoords by remember { mutableStateOf(Offset.Unspecified) }

        val previewMode by rememberPreference(PreferenceStore.PREVIEW_PERFORMANCE_MODE)

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
    val frequency by context.getPreferenceStateDefault(PreferenceStore.SCAN_FREQUENCY)
    val fullyInside by context.getPreferenceStateDefault(PreferenceStore.FULL_INSIDE)
    val scanRegex by context.getPreferenceStateDefault(PreferenceStore.SCAN_REGEX)
    val jsEnabled by context.getPreferenceStateDefault(PreferenceStore.ENABLE_JS)
    val jsCode by context.getPreferenceStateDefault(PreferenceStore.JS_CODE)

    LaunchedEffect(fullyInside, scanRegex, jsEnabled, jsCode, frequency, jsEngineService) {
        viewModel.updateScanParameters(
            fullyInside, runCatching {
                if (!scanRegex.isBlank()) scanRegex.toRegex() else null
            }.getOrNull(), if (jsEnabled) jsCode else null, frequency, jsEngineService
        )
    }

    // Barcode reader options
    val codeTypes by context.getPreferenceStateDefault(PreferenceStore.CODE_TYPES)
    val tryHarder by context.getPreferenceStateDefault(PreferenceStore.ADV_TRY_HARDER)
    val tryRotate by context.getPreferenceStateDefault(PreferenceStore.ADV_TRY_ROTATE)
    val tryInvert by context.getPreferenceStateDefault(PreferenceStore.ADV_TRY_INVERT)
    val tryDownscale by context.getPreferenceStateDefault(PreferenceStore.ADV_TRY_DOWNSCALE)
    val minLines by context.getPreferenceStateDefault(PreferenceStore.ADV_MIN_LINE_COUNT)
    val binarizer by context.getPreferenceStateDefault(PreferenceStore.ADV_BINARIZER)
    val downscaleFactor by context.getPreferenceStateDefault(PreferenceStore.ADV_DOWNSCALE_FACTOR)
    val downscaleThreshold by context.getPreferenceStateDefault(PreferenceStore.ADV_DOWNSCALE_THRESHOLD)
    val textMode by context.getPreferenceStateDefault(PreferenceStore.ADV_TEXT_MODE)

    LaunchedEffect(
        codeTypes,
        tryHarder,
        tryRotate,
        tryInvert,
        tryDownscale,
        minLines,
        binarizer,
        downscaleFactor,
        downscaleThreshold,
        textMode
    ) {
        viewModel.updateBarcodeReaderOptions(
            codeTypes,
            tryHarder,
            tryRotate,
            tryInvert,
            tryDownscale,
            minLines,
            binarizer,
            downscaleFactor,
            downscaleThreshold,
            textMode,
        )
    }
}