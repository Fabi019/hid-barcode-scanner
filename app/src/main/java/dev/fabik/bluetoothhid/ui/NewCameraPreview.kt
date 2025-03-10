package dev.fabik.bluetoothhid.ui

import android.graphics.PointF
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.view.CameraController
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.ui.model.NewCameraViewModel
import kotlinx.coroutines.delay
import java.util.UUID

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
@Composable
fun CameraPreviewContent(
    viewModel: NewCameraViewModel = viewModel<NewCameraViewModel>(),
    onCameraReady: (CameraController) -> Unit,
    onBarcodeDetected: (String, Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    var autofocusRequest by remember { mutableStateOf(UUID.randomUUID() to Offset.Unspecified) }

    val autofocusRequestId = autofocusRequest.first
    // Show the autofocus indicator if the offset is specified
    val showAutofocusIndicator = autofocusRequest.second.isSpecified
    // Cache the initial coords for each autofocus request
    val autofocusCoords = remember(autofocusRequestId) { autofocusRequest.second }

    // Queue hiding the request for each unique autofocus tap
    if (showAutofocusIndicator) {
        LaunchedEffect(autofocusRequestId) {
            delay(1000)
            // Clear the offset to finish the request and hide the indicator
            autofocusRequest = autofocusRequestId to Offset.Unspecified
        }
    }

    surfaceRequest?.let { request ->
        val coordinateTransformer = remember { MutableCoordinateTransformer() }

        CameraXViewfinder(
            surfaceRequest = request,
            coordinateTransformer = coordinateTransformer,
            modifier = Modifier.pointerInput(viewModel, coordinateTransformer) {
                detectTapGestures { tapCoords ->
                    with(coordinateTransformer) {
                        viewModel.tapToFocus(tapCoords.transform())
                    }
                    autofocusRequest = UUID.randomUUID() to tapCoords
                }
            }
        )

        AnimatedVisibility(
            visible = showAutofocusIndicator,
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
                    .size(48.dp))
        }

        val currentBarcode by viewModel.currentBarcode.collectAsStateWithLifecycle()

        Canvas(Modifier.fillMaxSize()) {
            currentBarcode?.let {
                val vw = size.width
                val vh = size.height

                val sw = it.size.width.toFloat()
                val sh = it.size.height.toFloat()

                val viewAspectRatio = vw / vh
                val sourceAspectRatio = sw / sh

                var scale = 1.0f
                var transX = 0.0f
                var transY = 0.0f

                if (sourceAspectRatio > viewAspectRatio) {
                    scale = vh / sh
                    transX = (sw * scale - vw) / 2
                    transY = 0f
                } else {
                    scale = vw / sw
                    transX = 0f
                    transY = (sh * scale - vh) / 2
                }

                // Draw a rectangle around the barcode
                val path = Path().apply {
                    it.cornerPoints.map {
                        PointF(it.x * scale - transX, it.y * scale - transY)
                    }.forEach { p ->
                        if (isEmpty)
                            moveTo(p.x, p.y)
                        lineTo(p.x, p.y)
                    }
                    close()
                }

                drawPath(path, color = Color.Blue, style = Stroke(5f))
            }
        }
    }
}