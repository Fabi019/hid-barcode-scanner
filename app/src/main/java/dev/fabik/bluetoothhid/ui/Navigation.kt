package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.History
import dev.fabik.bluetoothhid.LocalController
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.Scanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
    const val History = "History"
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

    var canExit by remember { mutableStateOf(false) }
    val exitString = stringResource(R.string.exit_confirm)

    // Handles shortcut to scanner
    val startDestination = remember {
        when (activity.intent.dataString) {
            "Scanner" -> Routes.Main
            "History" -> Routes.History
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

                // Confirm back presses to exit the app
                BackHandler {
                    if (canExit) {
                        activity.finishAfterTransition()
                    } else {
                        canExit = true
                        Toast.makeText(activity, exitString, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(2000)
                            canExit = false
                        }
                    }
                }
            }

            composable(Routes.Main) {
                Scanner(controller.currentDevice) {
                    CoroutineScope(Dispatchers.IO).launch {
                        controller.sendString(it)
                    }
                }

                BackHandler {
                    // Disconnect from device and navigate back to devices list
                    if (controller.disconnect()) {
                        if (!navController.navigateUp()) {
                            navController.popBackStack()
                            navController.navigate(Routes.Devices)
                        }
                    }
                }
            }

            composable(Routes.History) {
                // Go back either by pressing the back button or the back arrow
                val onBack: () -> Unit = {
                    if (!navController.navigateUp()) {
                        navController.popBackStack()
                        navController.navigate(Routes.Devices)
                    }
                }

                History(onBack) {
                    CoroutineScope(Dispatchers.IO).launch {
                        controller.sendString(it)
                    }
                }

                BackHandler(onBack = onBack)
            }
        }
    }

    // Listen for changes in the current device
    LaunchedEffect(controller.currentDevice) {
        // When connected to a device, navigate to the scanner
        if (controller.currentDevice != null) {
            // Single-top is used to avoid creating multiple instances of the scanner
            navController.navigate(Routes.Main) {
                launchSingleTop = true
            }
        }
    }
}
