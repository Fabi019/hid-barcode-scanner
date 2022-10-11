package dev.fabik.bluetoothhid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.Settings
import dev.fabik.bluetoothhid.bt.BluetoothController

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
    const val Settings = "Settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    bluetoothController: BluetoothController,
    onSendText: (String) -> Unit
) {
    NavHost(navController, startDestination = Routes.Devices) {
        composable(Routes.Devices) {
            Devices(navController, bluetoothController)
        }

        composable(Routes.Main) {
            Scanner(navController, bluetoothController, onSendText)
            BackHandler {
                if (!bluetoothController.disconnect()) {
                    navController.navigateUp()
                }
            }
        }

        composable(Routes.Settings) {
            Settings(navController)
        }
    }
}