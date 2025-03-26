package dev.fabik.bluetoothhid.ui

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.model.NewCameraViewModel
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference
import kotlinx.coroutines.launch

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
@Composable
fun CameraPreviewContent(
    viewModel: NewCameraViewModel = viewModel<NewCameraViewModel>(),
    onCameraReady: (CameraControl?, CameraInfo?) -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Camera settings
    val frontCamera by rememberPreference(PreferenceStore.FRONT_CAMERA)
    val resolution by rememberPreference(PreferenceStore.SCAN_RESOLUTION)
    val codeTypes by rememberPreference(PreferenceStore.CODE_TYPES)
    val previewMode by rememberPreference(PreferenceStore.PREVIEW_PERFORMANCE_MODE)

    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleOwner, frontCamera, resolution, codeTypes) {
        viewModel.bindToCamera(
            context.applicationContext,
            lifecycleOwner,
            frontCamera,
            resolution,
            codeTypes,
            onCameraReady = onCameraReady,
            onBarcode = onBarcodeDetected,
        )
    }

    // Scanner settings
    val frequency by rememberPreference(PreferenceStore.SCAN_FREQUENCY)
    val fullyInside by rememberPreference(PreferenceStore.FULL_INSIDE)
    val scanRegex by rememberPreference(PreferenceStore.SCAN_REGEX)
    val jsEnabled by rememberPreference(PreferenceStore.ENABLE_JS)
    val jsCode by rememberPreference(PreferenceStore.JS_CODE)

    LaunchedEffect(fullyInside, scanRegex, jsEnabled, jsCode, frequency) {
        viewModel.updateScanParameters(
            fullyInside, runCatching {
                if (!scanRegex.isBlank()) scanRegex.toRegex() else null
            }.getOrNull(), if (jsEnabled) jsCode else null, frequency
        )
    }

    surfaceRequest?.let { request ->
        var isFocusing by remember { mutableStateOf(false) }
        var autofocusCoords by remember { mutableStateOf(Offset.Unspecified) }

        val coordinateTransformer = remember { MutableCoordinateTransformer() }

        CameraXViewfinder(
            surfaceRequest = request,
            coordinateTransformer = coordinateTransformer,
            implementationMode = if (previewMode) ImplementationMode.EXTERNAL else ImplementationMode.EMBEDDED,
            modifier = Modifier.pointerInput(viewModel, coordinateTransformer) {
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
}