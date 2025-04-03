package dev.fabik.bluetoothhid.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun OverlayCanvas(viewModel: CameraViewModel) {
    val overlayType by rememberPreference(PreferenceStore.OVERLAY_TYPE)
    val restrictArea by rememberPreference(PreferenceStore.RESTRICT_AREA)
    val showPossible by rememberPreference(PreferenceStore.SHOW_POSSIBLE)
    // val highlightType by rememberPreferenceNull(PreferenceStore.HIGHLIGHT_TYPE)
    val developerMode by rememberPreference(PreferenceStore.DEVELOPER_MODE)

    val currentBarcode by viewModel.currentBarcode.collectAsStateWithLifecycle()

    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewModel.viewSize = it.size
                viewModel.lastScanSize = null
            }) {
        val x = this.size.width / 2
        val y = this.size.height / 2
        val landscape = this.size.width > this.size.height

        // Draws the scanner area
        if (restrictArea) {
            viewModel.scanRect = when (overlayType) {
                // Rectangle optimized for barcodes
                1 -> {
                    val length = this.size.width * 0.8f
                    val height = (length * 0.45f).coerceAtMost(y * 0.8f)
                    Rect(
                        Offset(x - length / 2, y - height / 2),
                        Size(length, height)
                    )
                }

                2 -> {
                    val pos = viewModel.overlayPosition
                    val size = viewModel.overlaySize

                    if (pos != null && size != null)
                        Rect(
                            pos + Offset(
                                x - size.width.absoluteValue,
                                y - size.height.absoluteValue
                            ),
                            pos + Offset(
                                x + size.width.absoluteValue,
                                y + size.height.absoluteValue
                            )
                        )
                    else Rect.Zero
                }

                // Square for scanning qr codes
                else -> {
                    val length = if (landscape) this.size.height * 0.6f else this.size.width * 0.8f
                    Rect(Offset(x - length / 2, y - length / 2), Size(length, length))
                }
            }

            val markerPath = Path().apply {
                addRoundRect(RoundRect(viewModel.scanRect, CornerRadius(30f)))
            }

            clipPath(markerPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black, topLeft = Offset.Zero, size = size, alpha = 0.5f)
            }

            drawPath(markerPath, color = Color.White, style = Stroke(5f))
        } else {
            viewModel.scanRect = Rect(Offset(0f, 0f), size)
        }

        // Highlights the current barcode on screen (with a rectangle)
        currentBarcode?.let {
            // Draw a rectangle around the barcode
            val path = Path().apply {
                it.cornerPoints.forEach { p ->
                    if (isEmpty)
                        moveTo(p.x, p.y)
                    lineTo(p.x, p.y)
                }
                close()
            }

            drawPath(path, color = Color.Blue, style = Stroke(5f))
        }
    }

    // Show the adjust buttons
    if (restrictArea && overlayType == 2) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CustomOverlayButtons(viewModel)
        }
    }

    // Draw debug overlay
    if (developerMode) {
        DebugOverlay(viewModel)
    }
}

@Composable
fun CustomOverlayButtons(viewModel: CameraViewModel) {
    val context = LocalContext.current
    var posOffsetX by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_POS_X
            ).first()
        })
    }
    var posOffsetY by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_POS_Y
            ).first()
        })
    }
    var sizeOffsetX by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_WIDTH
            ).first()
        })
    }
    var sizeOffsetY by remember {
        mutableFloatStateOf(runBlocking {
            context.getPreference(
                PreferenceStore.OVERLAY_HEIGHT
            ).first()
        })
    }

    LaunchedEffect(Unit) {
        viewModel.overlayPosition = Offset(posOffsetX, posOffsetY)
        viewModel.overlaySize = Size(sizeOffsetX, sizeOffsetY)
    }

    fun saveState() {
        runBlocking {
            context.setPreference(PreferenceStore.OVERLAY_POS_X, posOffsetX)
            context.setPreference(PreferenceStore.OVERLAY_POS_Y, posOffsetY)
            context.setPreference(PreferenceStore.OVERLAY_WIDTH, sizeOffsetX)
            context.setPreference(PreferenceStore.OVERLAY_HEIGHT, sizeOffsetY)
        }
    }

    fun reset() {
        posOffsetX = PreferenceStore.OVERLAY_POS_X.defaultValue
        posOffsetY = PreferenceStore.OVERLAY_POS_Y.defaultValue
        sizeOffsetX = PreferenceStore.OVERLAY_WIDTH.defaultValue
        sizeOffsetY = PreferenceStore.OVERLAY_HEIGHT.defaultValue

        viewModel.overlayPosition = Offset(posOffsetX, posOffsetY)
        viewModel.overlaySize = Size(sizeOffsetX, sizeOffsetY)

        saveState()
    }

    IconButton(
        onClick = ::reset,
        colors = IconButtonDefaults.iconButtonColors(
            MaterialTheme.colorScheme.surface.copy(
                alpha = 0.5f
            )
        ),
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    (posOffsetX + sizeOffsetX).roundToInt(),
                    (posOffsetY + sizeOffsetY).roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = ::saveState) { change, dragAmount ->
                    change.consume()
                    sizeOffsetX += dragAmount.x
                    sizeOffsetY += dragAmount.y
                    viewModel.overlaySize = Size(sizeOffsetX, sizeOffsetY)
                }
            }
    ) {
        Icon(Icons.Default.OpenInFull, "Modify size")
    }

    IconButton(
        onClick = ::reset,
        colors = IconButtonDefaults.iconButtonColors(
            MaterialTheme.colorScheme.surface.copy(
                alpha = 0.5f
            )
        ),
        modifier = Modifier
            .absoluteOffset {
                IntOffset(
                    (posOffsetX - sizeOffsetX).roundToInt(),
                    (posOffsetY + sizeOffsetY).roundToInt()
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = ::saveState) { change, dragAmount ->
                    change.consume()
                    posOffsetX += dragAmount.x
                    posOffsetY += dragAmount.y
                    viewModel.overlayPosition = Offset(posOffsetX, posOffsetY)
                }
            }
    ) {
        Icon(Icons.Default.DragIndicator, "Modify position")
    }
}

@Composable
fun DebugOverlay(viewModel: CameraViewModel) {
    val cameraTrace by viewModel.cameraTrace.state.collectAsStateWithLifecycle()
    val detectorTrace by viewModel.detectorTrace.state.collectAsStateWithLifecycle()

    Canvas(Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas

        // Using canvas.width/height returns fullscreen instead of the real size
        val y = size.height * 0.6f

        // Draw the camera fps
        canvas.drawText(
            "FPS: ${cameraTrace?.currentFps}, Frame latency: ${cameraTrace?.currentLatency} ms",
            10f,
            y,
            Paint().apply {
                textSize = 50f
                color = Color.White.toArgb()
            }
        )

        // Draw the detector stats
        canvas.drawText(
            "Detector latency: ${detectorTrace?.currentLatency} ms (Delta: ${
                detectorTrace?.currentLatency?.minus(
                    cameraTrace?.currentLatency ?: 0
                )
            } ms)",
            10f,
            y + 50f,
            Paint().apply {
                textSize = 50f
                color = Color.White.toArgb()
            }
        )

        // Draw the input image size
        canvas.drawText(
            "Image size: ${viewModel.lastScanSize?.width}x${viewModel.lastScanSize?.height}",
            10f,
            y + 100f,
            Paint().apply {
                color = Color.White.toArgb()
                textSize = 50f
            }
        )

        // Draw the preview image size
        canvas.drawText(
            "Preview size: ${viewModel.viewSize?.width}x${viewModel.viewSize?.height}",
            10f,
            y + 150f,
            Paint().apply {
                color = Color.White.toArgb()
                textSize = 50f
            }
        )

        // Draw the custom selection
        canvas.drawText(
            "Selector size: ${viewModel.overlaySize?.width?.roundToInt()}x${viewModel.overlaySize?.height?.roundToInt()} " +
                    "at (${viewModel.overlayPosition?.x?.roundToInt()}, ${viewModel.overlayPosition?.y?.roundToInt()})",
            10f,
            y + 200f,
            Paint().apply {
                color = Color.White.toArgb()
                textSize = 50f
            }
        )

        // Draw the histogram
        fun drawHistogram(values: Iterable<Float>, increment: Float, paint: Paint) {
            val path = android.graphics.Path()

            values.forEachIndexed { index, value ->
                if (index == 0) {
                    path.moveTo(0f, size.height - value.coerceAtMost(size.height))
                } else {
                    path.lineTo(
                        index * increment,
                        size.height - value.coerceAtMost(size.height)
                    )
                }
            }

            canvas.drawPath(path, paint.apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                alpha = 100
            })
        }

        detectorTrace?.let {
            drawHistogram(
                it.history,
                size.width / (it.history.maxSize - 1),
                Paint().apply {
                    color = Color.Green.toArgb()
                }
            )
        }

        cameraTrace?.let {
            drawHistogram(
                it.history,
                size.width / (it.history.maxSize - 1),
                Paint().apply {
                    color = Color.Red.toArgb()
                }
            )
        }
    }
}