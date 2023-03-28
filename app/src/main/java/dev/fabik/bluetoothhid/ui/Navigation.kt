package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.bluetooth.BluetoothProfile
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.Scanner
import kotlinx.coroutines.launch

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
}

val LocalNavigation = staticCompositionLocalOf<NavHostController> {
    error("No Navigation provided")
}

@Composable
fun NavGraph() {
    val controller = LocalController.current
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    // Handles shortcut to scanner
    val startDestination = remember {
        when (activity.intent.dataString) {
            "Scanner" -> Routes.Main
            else -> Routes.Devices
        }
    }

    CompositionLocalProvider(LocalNavigation provides navController) {
        NavHost(
            navController,
            startDestination,
        ) {
            composable(Routes.Devices) {
                Devices()

                // If the user presses the back button, close the app
                BackHandler {
                    activity.finishAfterTransition()
                }
            }

            composable(Routes.Main) {
                // Disconnect from device and navigate back to devices list
                val onBack: () -> Unit = {
                    controller.disconnect()
                    if (!navController.navigateUp()) {
                        navController.popBackStack()
                        navController.navigate(Routes.Devices)
                    }
                }

                Scanner(controller.currentDevice, onBack) {
                    scope.launch {
                        controller.sendString(it)
                    }
                }

                BackHandler(onBack = onBack)
            }
        }
    }

    DisposableEffect(controller) {
        val listener = controller.registerListener { device, state ->
            // Navigation calls need to be from a UI-Thread
            activity.runOnUiThread {
                // Check if connection to a device has been established, else go back to the device list
                if (device != null && state == BluetoothProfile.STATE_CONNECTED) {
                    navController.navigate(Routes.Main) {
                        launchSingleTop = true
                    }
                }
            }
        }

        onDispose {
            controller.unregisterListener(listener)
        }
    }
}
