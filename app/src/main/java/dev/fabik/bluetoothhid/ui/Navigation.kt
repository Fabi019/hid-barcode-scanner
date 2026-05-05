package dev.fabik.bluetoothhid.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.History
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.utils.ZXingAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Routes : NavKey {
    Devices, Main, History
}

val LocalNavigation = staticCompositionLocalOf<NavBackStack<NavKey>> {
    error("No Navigation provided")
}

@Composable
fun NavGraph() {
    val controller = LocalController.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()

    var canExit by remember { mutableStateOf(false) }
    val exitString = stringResource(R.string.exit_confirm)

    // Handles shortcut to scanner
    val startDestinations = remember {
        when (activity?.intent?.dataString) {
            "Scanner" -> arrayOf(Routes.Devices, Routes.Main)
            "History" -> arrayOf(Routes.Devices, Routes.History)
            else -> arrayOf(Routes.Devices)
        }
    }

    val currentDevice by controller?.currentDevice?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    val backStack = rememberNavBackStack(*startDestinations)

    CompositionLocalProvider(LocalNavigation provides backStack) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                when (backStack.removeLastOrNull()) {
                    Routes.Main -> controller?.disconnect()
                    else -> {}
                }
            },
            entryProvider = { key ->
                when (key) {
                    Routes.Main -> NavEntry(key) {
                        Scanner(currentDevice) { text, format, imageName ->
                            scope.launch {
                                val barcodeType = format?.let { ZXingAnalyzer.index2String(it) }
                                controller?.sendString(
                                    text,
                                    true,
                                    "SCAN",
                                    null,
                                    barcodeType,
                                    imageName = imageName
                                )
                            }
                        }
                    }

                    Routes.History -> NavEntry(key) {
                        History { historyEntry ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val barcodeType =
                                    historyEntry.format.let { ZXingAnalyzer.index2String(it) }
                                controller?.sendString(
                                    historyEntry.value,
                                    true,
                                    "HISTORY",
                                    historyEntry.timestamp,
                                    barcodeType
                                )
                            }
                        }
                    }

                    else -> NavEntry(key) {
                        // Confirm back presses to exit the app
                        BackHandler {
                            if (canExit) {
                                activity?.finishAfterTransition()
                            } else {
                                canExit = true
                                Toast.makeText(activity, exitString, Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000)
                                    canExit = false
                                }
                            }
                        }

                        Devices()
                    }
                }
            }
        )
    }


//    CompositionLocalProvider(LocalNavigation provides navController) {
//        NavHost(
//            navController,
//            startDestination,
//            popExitTransition = {
//                scaleOut(
//                    targetScale = 0.9f,
//                    transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.5f)
//                )
//            },
//        ) {
//            composable(Routes.Devices) {
//                Devices()

// Confirm back presses to exit the app
//                BackHandler {
//                    if (canExit) {
//                        activity?.finishAfterTransition()
//                    } else {
//                        canExit = true
//                        Toast.makeText(activity, exitString, Toast.LENGTH_SHORT).show()
//                        scope.launch {
//                            delay(2000)
//                            canExit = false
//                        }
//                    }
//                }
//            }
//
//            composable(Routes.Main) {
//                Scanner(currentDevice) { text, format, imageName ->
//                    scope.launch {
//                        val barcodeType = format?.let { ZXingAnalyzer.index2String(it) }
//                        controller?.sendString(
//                            text,
//                            true,
//                            "SCAN",
//                            null,
//                            barcodeType,
//                            imageName = imageName
//                        )
//                    }
//                }

//                BackHandler {
//                    // Disconnect from device and navigate back to devices list
//                    controller?.disconnect()
//                    if (!navController.navigateUp()) {
//                        navController.popBackStack()
//                        navController.navigate(Routes.Devices)
//                    }
//                }
//            }
//
//            composable(Routes.History) {
//                // Go back either by pressing the back button or the back arrow
//                val onBack: () -> Unit = {
//                    if (!navController.navigateUp()) {
//                        navController.popBackStack()
//                        navController.navigate(Routes.Devices)
//                    }
//                }
//
//                History(onBack) { historyEntry ->
//                    CoroutineScope(Dispatchers.IO).launch {
//                        val barcodeType = historyEntry.format.let { ZXingAnalyzer.index2String(it) }
//                        controller?.sendString(historyEntry.value, true, "HISTORY", historyEntry.timestamp, barcodeType)
//                    }
//                }

//BackHandler(onBack = onBack)
//            }
//        }
//    }

    // Listen for changes in the current device
    LaunchedEffect(currentDevice) {
        // When connected to a device, navigate to the scanner
        if (currentDevice != null && !backStack.contains(Routes.Main)) {
            backStack.add(Routes.Main)
        }
    }
}