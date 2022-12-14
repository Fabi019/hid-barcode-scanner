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
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.bt.BluetoothController
import kotlinx.coroutines.launch

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
}

val LocalNavigation = staticCompositionLocalOf<NavHostController> {
    error("No Navigation provided")
}

@Composable
fun NavGraph(controller: BluetoothController) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    CompositionLocalProvider(LocalNavigation provides navController) {
        NavHost(
            navController,
            startDestination = Routes.Devices,
        ) {
            composable(Routes.Devices) {
                Devices(controller)

                // If the user presses the back button, close the app
                BackHandler {
                    activity.finishAfterTransition()
                }
            }

            composable(Routes.Main) {
                // Disconnect from device and navigate back to devices list
                val onBack: () -> Unit = {
                    controller.disconnect()
                    navController.navigateUp()
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