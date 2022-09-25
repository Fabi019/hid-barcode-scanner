package dev.fabik.bluetoothhid.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.fabik.bluetoothhid.DeviceScreen
import dev.fabik.bluetoothhid.MainScreen
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
    bluetoothController: BluetoothController
) {
    NavHost(navController, startDestination = Routes.Devices) {
        composable(Routes.Devices) {
            DeviceScreen(navController, bluetoothController)
        }

        composable(Routes.Main) {
            MainScreen(navController, bluetoothController)
        }

        composable(Routes.Settings) {
            Settings(navController)
        }
    }
}