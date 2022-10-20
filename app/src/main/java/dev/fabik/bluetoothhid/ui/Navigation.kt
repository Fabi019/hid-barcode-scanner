package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.bluetooth.BluetoothProfile
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.Settings

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
    const val Settings = "Settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val controller = LocalController.current

    NavHost(navController, startDestination = Routes.Devices) {
        composable(Routes.Devices) {
            Devices(navController)
            BackHandler {
                (context as Activity).finishAfterTransition()
            }
        }

        composable(Routes.Main) {
            val disconnectOrBack = {
                if (!controller.disconnect()) {
                    navController.navigateUp()
                }
            }

            Scanner(navController, controller.currentDevice(), disconnectOrBack) {
                controller.keyboardSender?.sendString(it)
            }
            BackHandler(onBack = disconnectOrBack)
        }

        composable(Routes.Settings) {
            Settings(navController)
        }
    }

    DisposableEffect(controller) {
        val listener = controller.registerListener { device, state ->
            (context as Activity).runOnUiThread {
                if (device != null && state == BluetoothProfile.STATE_CONNECTED) {
                    navController.navigate(Routes.Main) {
                        launchSingleTop = true
                    }
                } else {
                    navController.navigate(Routes.Devices) {
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