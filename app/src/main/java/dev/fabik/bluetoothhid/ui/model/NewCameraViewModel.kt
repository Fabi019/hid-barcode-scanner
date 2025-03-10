package dev.fabik.bluetoothhid.ui.model

import android.content.Context
import android.graphics.Point
import android.util.Size
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

// based on: https://medium.com/androiddevelopers/getting-started-with-camerax-in-jetpack-compose-781c722ca0c4
class NewCameraViewModel : ViewModel() {
    // Used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()
    private var surfaceMeteringPointFactory: SurfaceOrientedMeteringPointFactory? = null
    private var cameraControl: CameraControl? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
            surfaceMeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                newSurfaceRequest.resolution.width.toFloat(),
                newSurfaceRequest.resolution.height.toFloat()
            )
        }
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        val barcodeReader = ZXingAnalyzer(
            0,
            onAnalyze = {},
            onResult = { result, size -> onBarcodeResult(result, size) })

        val analyzer = ImageAnalysis.Builder()
            //.setResolutionSelector(resolutionSelector)
            .setOutputImageRotationEnabled(true)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analyzer.setAnalyzer(Executors.newSingleThreadExecutor(), barcodeReader)

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(cameraPreviewUseCase)
            .addUseCase(analyzer)
            .build()

        val camera = processCameraProvider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup
        )

        cameraControl = camera.cameraControl

        // Cancellation signals we're done with the camera
        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
            cameraControl = null
        }
    }

    private val _currentBarcode = MutableStateFlow<Barcode?>(null)
    val currentBarcode: StateFlow<Barcode?> = _currentBarcode.asStateFlow()

    fun onBarcodeResult(result: List<BarcodeReader.Result>, source: Size) {
        result.firstOrNull()?.let {
            val cornerPoints = listOf(
                it.position.topLeft,
                it.position.topRight,
                it.position.bottomRight,
                it.position.bottomLeft
            )

            _currentBarcode.update { _ -> Barcode(it.text, cornerPoints, source) }
        } ?: run {
            _currentBarcode.update { _ -> null }
        }
    }

    data class Barcode(val value: String?, val cornerPoints: List<Point>, val size: Size)

    fun tapToFocus(tapCoords: Offset) {
        val point = surfaceMeteringPointFactory?.createPoint(tapCoords.x, tapCoords.y)
        if (point != null) {
            val meteringAction = FocusMeteringAction.Builder(point).build()
            cameraControl?.startFocusAndMetering(meteringAction)
        }
    }
}