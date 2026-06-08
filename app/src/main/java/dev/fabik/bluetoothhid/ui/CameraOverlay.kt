package dev.fabik.bluetoothhid.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableStateOf
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
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.bt.TcpStatusData
import dev.fabik.bluetoothhid.ui.model.CameraViewModel
import dev.fabik.bluetoothhid.utils.isTcpMode
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
    val showTcpStatus by context.getPreferenceState(PreferenceStore.SHOW_TCP_STATUS)
    val connectionMode by context.getPreferenceState(PreferenceStore.CONNECTION_MODE)
    val isTcpMode = connectionMode.isTcpMode()
    val controller = LocalController.current
    val tcpStatus by controller?.tcpStatusFlow?.collectAsStateWithLifecycle(TcpStatusData())
        ?: remember { mutableStateOf(TcpStatusData()) }

    UpdateScanAreas(viewModel, restrictArea, overlayType)

    val currentBarcode by viewModel.currentBarcode.collectAsStateWithLifecycle()
    val detectedBarcodes by viewModel.detectedBarcodes.collectAsStateWithLifecycle()

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
        viewModel.clearScanAreas()
        viewModel.addScanAreas(*loaded.toTypedArray())
    }

    fun saveState() {
        runBlocking {
            context.setPreference(
                PreferenceStore.SCAN_AREAS,
                ScanAreaData.toJsonArray(viewModel.areas.value)
            )
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                viewModel.viewSize = it.size
                viewModel.lastScanSize = null
            }
    ) {
        // Draws the scanner area
        if (restrictArea) {
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
            CustomOverlayButtons(viewModel, ::saveState)
        }
    }

    // Draw debug overlay
    if (developerMode == true) {
        DebugOverlay(viewModel)
    }

    // Draw TCP connection status overlay
    if (showTcpStatus == true && isTcpMode && !tcpStatus.isEmpty) {
        TcpStatusOverlay(tcpStatus)
    }
}

@Composable
fun UpdateScanAreas(
    viewModel: CameraViewModel,
    restrictArea: Boolean,
    overlayType: OverlayType
) {
    LaunchedEffect(viewModel.viewSize, restrictArea, overlayType) {
        if (!restrictArea) return@LaunchedEffect
        viewModel.viewSize?.let {
            val x = it.width / 2
            val y = it.height / 2
            val landscape = it.width > it.height

            viewModel.areas.collect { scanAreas ->
                viewModel.scanRect = when (overlayType) {
                    // Rectangle optimized for barcodes
                    OverlayType.RECTANGLE -> {
                        viewModel.scanRects = emptyList()
                        val length = it.width * 0.8f
                        val height = (length * 0.45f).coerceAtMost(y * 0.8f)
                        Rect(
                            Offset(x - length / 2, y - height / 2),
                            Size(length, height)
                        )
                    }

                    // Custom — user adjustable, supports multiple areas
                    OverlayType.CUSTOM -> {
                        val rects =
                            scanAreas.map { a -> a.toRect(it.width.toFloat(), it.height.toFloat()) }
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
                        val length = if (landscape) it.height * 0.6f else it.width * 0.8f
                        Rect(Offset(x - length / 2, y - length / 2), Size(length, length))
                    }
                }
            }
        }
    }
}

@Composable
fun CustomOverlayButtons(
    viewModel: CameraViewModel,
    saveState: () -> Unit
) {
    val scanAreas by viewModel.areas.collectAsStateWithLifecycle()

    fun reset() {
        viewModel.clearScanAreas()
        viewModel.addScanAreas(ScanAreaData.DEFAULT)
        saveState()
    }

    val iconColors = IconButtonDefaults.iconButtonColors(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    )

    scanAreas.forEachIndexed { index, area ->
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
                .pointerInput(scanAreas.size) {
                    detectDragGestures(onDragEnd = saveState) { change, dragAmount ->
                        change.consume()
                        viewModel.updateScanArea(
                            index, scanAreas[index].copy(
                                sizeX = scanAreas[index].sizeX + dragAmount.x,
                                sizeY = scanAreas[index].sizeY + dragAmount.y
                            )
                        )
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
                .pointerInput(scanAreas.size) {
                    detectDragGestures(onDragEnd = saveState) { change, dragAmount ->
                        change.consume()
                        viewModel.updateScanArea(
                            index, scanAreas[index].copy(
                                posX = scanAreas[index].posX + dragAmount.x,
                                posY = scanAreas[index].posY + dragAmount.y
                            )
                        )
                    }
                }
        ) {
            Icon(Icons.Default.DragIndicator, "Modify position")
        }

        // Delete button — top-right corner, only shown when multiple areas exist
        if (scanAreas.size > 1) {
            IconButton(
                onClick = {
                    viewModel.removeScanArea(index)
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

}

@Composable
fun DebugOverlay(viewModel: CameraViewModel) {
    val cameraTrace by viewModel.cameraTrace.state.collectAsStateWithLifecycle()
    val detectorTrace by viewModel.detectorTrace.state.collectAsStateWithLifecycle()
    val textPaint = remember {
        Paint().apply {
            textSize = 50f
            color = Color.White.toArgb()
        }
    }
    val histogramDetectorPaint = remember {
        Paint().apply { color = Color.Green.toArgb() }
    }
    val histogramCameraPaint = remember {
        Paint().apply { color = Color.Red.toArgb() }
    }

    Canvas(Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas

        // Using canvas.width/height returns fullscreen instead of the real size
        val y = size.height * 0.6f

        // Draw the camera fps
        canvas.drawText(
            "FPS: ${cameraTrace?.currentFps}, Frame latency: ${cameraTrace?.currentLatency} ms",
            10f,
            y,
            textPaint
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
            textPaint
        )

        // Draw the input image size
        canvas.drawText(
            "Image size: ${viewModel.lastScanSize?.width}x${viewModel.lastScanSize?.height}",
            10f,
            y + 100f,
            textPaint
        )

        // Draw the preview image size
        canvas.drawText(
            "Preview size: ${viewModel.viewSize?.width}x${viewModel.viewSize?.height}",
            10f,
            y + 150f,
            textPaint
        )

        // Draw the custom selection
        canvas.drawText(
            "Selector size: ${viewModel.overlaySize?.width?.roundToInt()}x${viewModel.overlaySize?.height?.roundToInt()} " +
                    "at (${viewModel.overlayPosition?.x?.roundToInt()}, ${viewModel.overlayPosition?.y?.roundToInt()})",
            10f,
            y + 200f,
            textPaint
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
            drawHistogram(it.history, size.width / (it.history.maxSize - 1), histogramDetectorPaint)
        }

        cameraTrace?.let {
            drawHistogram(it.history, size.width / (it.history.maxSize - 1), histogramCameraPaint)
        }
    }
}

@Composable
fun TcpStatusOverlay(status: TcpStatusData) {
    val clientsLabel = stringResource(R.string.tcp_status_overlay_clients)
    val serverLabel = stringResource(R.string.tcp_status_overlay_server)
    val paintNormal = remember {
        Paint().apply {
            textSize = 50f
            color = Color.White.toArgb()
            textAlign = Paint.Align.RIGHT
        }
    }
    val paintDim = remember {
        Paint().apply {
            textSize = 40f
            color = Color.White.copy(alpha = 0.6f).toArgb()
            textAlign = Paint.Align.RIGHT
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        val x = size.width - 10f
        var y = size.height * 0.6f
        val lineHeight = 50f
        val dimLineHeight = 44f

        if (status.clientTarget != null) {
            // Client mode: show server address with label
            canvas.drawText(serverLabel, x, y, paintDim)
            y += lineHeight
            canvas.drawText(status.clientTarget, x, y, paintNormal)
        } else {
            // Server mode: server IPs, then clients section
            status.serverAddresses.forEach { addr ->
                canvas.drawText(addr, x, y, paintNormal)
                y += lineHeight
            }
            val clientCount = status.clientAddresses.size
            canvas.drawText("$clientsLabel [$clientCount]", x, y, paintDim)
            y += dimLineHeight
            status.clientAddresses.forEach { addr ->
                canvas.drawText(addr, x, y, paintNormal)
                y += lineHeight
            }
        }
    }
}