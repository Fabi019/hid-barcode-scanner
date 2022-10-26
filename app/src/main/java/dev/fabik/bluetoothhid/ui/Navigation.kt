package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.bluetooth.BluetoothProfile
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.Settings
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
    const val Settings = "Settings"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    controller: BluetoothController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(navController, startDestination = Routes.Devices) {
        composable(Routes.Devices) {
            Devices(navController, controller)
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
                scope.launch {
                    controller.keyboardSender?.sendString(
                        it,
                        context.getPreference(PreferenceStore.SEND_DELAY).first().toLong(),
                        context.getPreference(PreferenceStore.EXTRA_KEYS).first(),
                        when (context.getPreference(PreferenceStore.KEYBOARD_LAYOUT).first()) {
                            1 -> "de"
                            else -> "us"
                        }
                    )
                }
            }
            BackHandler(onBack = disconnectOrBack)
        }

        composable(Routes.Settings) {
            Settings {
                navController.navigateUp()
            }
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
