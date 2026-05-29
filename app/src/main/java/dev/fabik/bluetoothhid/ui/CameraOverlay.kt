package dev.fabik.bluetoothhid.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import dev.fabik.bluetoothhid.utils.OverlayType
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.ScanAreaData
import dev.fabik.bluetoothhid.utils.getPreference
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.getPreferenceStateBlocking
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun OverlayCanvas(viewModel: CameraViewModel) {
    val context = LocalContext.current

    val overlayTypeOrdinal by context.getPreferenceStateBlocking(PreferenceStore.OVERLAY_TYPE)
    val overlayType = OverlayType.fromIndex(overlayTypeOrdinal)
    val restrictArea by context.getPreferenceStateBlocking(PreferenceStore.RESTRICT_AREA)
    // val showPossible by rememberPreference(PreferenceStore.SHOW_POSSIBLE)
    // val highlightType by rememberPreferenceNull(PreferenceStore.HIGHLIGHT_TYPE)
    val developerMode by context.getPreferenceState(PreferenceStore.DEVELOPER_MODE)

    val currentBarcode by viewModel.currentBarcode.collectAsStateWithLifecycle()
    val detectedBarcodes by viewModel.detectedBarcodes.collectAsStateWithLifecycle()

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
                OverlayType.RECTANGLE -> {
                    viewModel.scanRects = emptyList()
                    val length = this.size.width * 0.8f
                    val height = (length * 0.45f).coerceAtMost(y * 0.8f)
                    Rect(
                        Offset(x - length / 2, y - height / 2),
                        Size(length, height)
                    )
                }

                // Custom — user adjustable, supports multiple areas
                OverlayType.CUSTOM -> {
                    val rects = viewModel.scanAreas.map { it.toRect(this.size.width, this.size.height) }
                    viewModel.scanRects = rects
                    when {
                        rects.isEmpty() -> Rect.Zero
                        rects.size == 1 -> rects.first()
                        else -> rects.fold(rects.first()) { acc, r ->
                            Rect(
                                minOf(acc.left, r.left), minOf(acc.top, r.top),
                                maxOf(acc.right, r.right), maxOf(acc.bottom, r.bottom)
                            )
                        }
                    }
                }

                // Square for scanning qr codes (default)
                OverlayType.SQUARE -> {
                    viewModel.scanRects = emptyList()
                    val length = if (landscape) this.size.height * 0.6f else this.size.width * 0.8f
                    Rect(Offset(x - length / 2, y - length / 2), Size(length, length))
                }
            }

            val markerPath = Path().apply {
                if (viewModel.scanRects.isNotEmpty()) {
                    // Draw each custom area as a separate rounded rect cutout
                    viewModel.scanRects.forEach { rect ->
                        addRoundRect(RoundRect(rect, CornerRadius(30f)))
                    }
                } else {
                    addRoundRect(RoundRect(viewModel.scanRect, CornerRadius(30f)))
                }
            }

            clipPath(markerPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black, topLeft = Offset.Zero, size = size, alpha = 0.5f)
            }

            drawPath(markerPath, color = Color.White, style = Stroke(5f))
        } else {
            viewModel.scanRect = Rect(Offset(0f, 0f), size)
            viewModel.scanRects = emptyList()
        }

        // In multi-code mode: highlight all detected barcodes; current = blue, others = yellow
        // In single-code mode: only currentBarcode is shown (detectedBarcodes has at most 1 item)
        detectedBarcodes.forEach { barcode ->
            val isActive = barcode.value != null && barcode.value == currentBarcode?.value
            val color = if (isActive) Color.Blue else Color.Yellow
            val path = Path().apply {
                barcode.cornerPoints.forEach { p ->
                    if (isEmpty) moveTo(p.x, p.y)
                    lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, color = color, style = Stroke(5f))
        }
    }

    // Show the adjust buttons
    if (restrictArea && overlayType == OverlayType.CUSTOM) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CustomOverlayButtons(viewModel)
        }
    }

    // Draw debug overlay
    if (developerMode == true) {
        DebugOverlay(viewModel)
    }
}

@Composable
fun CustomOverlayButtons(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val areas = remember { mutableStateListOf<ScanAreaData>() }

    LaunchedEffect(Unit) {
        val json = context.getPreference(PreferenceStore.SCAN_AREAS).first()
        val loaded = if (json.isBlank()) {
            // Migrate from legacy single-area prefs
            val posX = context.getPreference(PreferenceStore.OVERLAY_POS_X).first()
            val posY = context.getPreference(PreferenceStore.OVERLAY_POS_Y).first()
            val sizeX = context.getPreference(PreferenceStore.OVERLAY_WIDTH).first()
            val sizeY = context.getPreference(PreferenceStore.OVERLAY_HEIGHT).first()
            listOf(ScanAreaData(posX, posY, sizeX, sizeY))
        } else {
            ScanAreaData.fromJsonArray(json).ifEmpty { listOf(ScanAreaData.DEFAULT) }
        }
        areas.clear()
        areas.addAll(loaded)
        viewModel.scanAreas = areas.toList()
        // Keep legacy fields in sync with first area for debug overlay
        viewModel.overlayPosition = Offset(areas[0].posX, areas[0].posY)
        viewModel.overlaySize = Size(areas[0].sizeX, areas[0].sizeY)
    }

    fun saveState() {
        runBlocking {
            context.setPreference(PreferenceStore.SCAN_AREAS, ScanAreaData.toJsonArray(areas))
        }
    }

    fun reset() {
        areas.clear()
        areas.add(ScanAreaData.DEFAULT)
        viewModel.scanAreas = areas.toList()
        viewModel.overlayPosition = Offset(ScanAreaData.DEFAULT.posX, ScanAreaData.DEFAULT.posY)
        viewModel.overlaySize = Size(ScanAreaData.DEFAULT.sizeX, ScanAreaData.DEFAULT.sizeY)
        saveState()
    }

    val iconColors = IconButtonDefaults.iconButtonColors(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    )

    areas.forEachIndexed { index, area ->
        // Resize handle — bottom-right corner of this area
        IconButton(
            onClick = ::reset,
            colors = iconColors,
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (area.posX + area.sizeX.absoluteValue).roundToInt(),
                        (area.posY + area.sizeY.absoluteValue).roundToInt()
                    )
                }
                .pointerInput(areas.size) {
                    detectDragGestures(onDragEnd = ::saveState) { change, dragAmount ->
                        change.consume()
                        areas[index] = areas[index].copy(
                            sizeX = areas[index].sizeX + dragAmount.x,
                            sizeY = areas[index].sizeY + dragAmount.y
                        )
                        viewModel.scanAreas = areas.toList()
                    }
                }
        ) {
            Icon(Icons.Default.OpenInFull, "Modify size")
        }

        // Move handle — bottom-left corner of this area
        IconButton(
            onClick = ::reset,
            colors = iconColors,
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(
                        (area.posX - area.sizeX.absoluteValue).roundToInt(),
                        (area.posY + area.sizeY.absoluteValue).roundToInt()
                    )
                }
                .pointerInput(areas.size) {
                    detectDragGestures(onDragEnd = ::saveState) { change, dragAmount ->
                        change.consume()
                        areas[index] = areas[index].copy(
                            posX = areas[index].posX + dragAmount.x,
                            posY = areas[index].posY + dragAmount.y
                        )
                        viewModel.scanAreas = areas.toList()
                        // Keep legacy fields in sync with first area for debug overlay
                        if (index == 0) {
                            viewModel.overlayPosition = Offset(areas[0].posX, areas[0].posY)
                            viewModel.overlaySize = Size(areas[0].sizeX, areas[0].sizeY)
                        }
                    }
                }
        ) {
            Icon(Icons.Default.DragIndicator, "Modify position")
        }

        // Delete button — top-right corner, only shown when multiple areas exist
        if (areas.size > 1) {
            IconButton(
                onClick = {
                    areas.removeAt(index)
                    viewModel.scanAreas = areas.toList()
                    saveState()
                },
                colors = iconColors,
                modifier = Modifier.absoluteOffset {
                    IntOffset(
                        (area.posX + area.sizeX.absoluteValue).roundToInt(),
                        (area.posY - area.sizeY.absoluteValue).roundToInt()
                    )
                }
            ) {
                Icon(Icons.Default.Close, "Delete area")
            }
        }
    }

    // Add area button — fixed offset from center so it doesn't overlap with handles
    IconButton(
        onClick = {
            val offset = areas.size * 40f
            areas.add(ScanAreaData(offset, offset, 100f, 100f))
            viewModel.scanAreas = areas.toList()
            saveState()
        },
        colors = IconButtonDefaults.iconButtonColors(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ),
        modifier = Modifier.absoluteOffset { IntOffset(-200, -200) }
    ) {
        Icon(Icons.Default.Add, "Add area")
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