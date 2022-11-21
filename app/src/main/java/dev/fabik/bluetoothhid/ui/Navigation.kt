package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.bluetooth.BluetoothProfile
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import dev.fabik.bluetoothhid.Devices
import dev.fabik.bluetoothhid.Scanner
import dev.fabik.bluetoothhid.Settings
import dev.fabik.bluetoothhid.bt.BluetoothController
import kotlinx.coroutines.launch

object Routes {
    const val Devices = "Devices"
    const val Main = "Main"
    const val Settings = "Settings"
}

val LocalNavigation = staticCompositionLocalOf<NavController> {
    error("No Navigation provided")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(controller: BluetoothController) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    val navController = rememberAnimatedNavController()

    val slideDistance = LocalDensity.current.run { 30.dp.roundToPx() }

    CompositionLocalProvider(LocalNavigation provides navController) {
        AnimatedNavHost(
            navController,
            startDestination = activity.intent.dataString ?: Routes.Devices,
            enterTransition = { inAnimation(true, slideDistance) },
            exitTransition = { outAnimation(true, slideDistance) },
            popEnterTransition = { inAnimation(false, slideDistance) },
            popExitTransition = { outAnimation(false, slideDistance) }
        ) {
            composable(Routes.Devices) {
                Devices(controller)

                // If the user presses the back button, close the app
                BackHandler {
                    activity.finishAfterTransition()
                }
            }

            composable(Routes.Main) {
                // First try to disconnect from the device.
                // If it fails (e.g. not connected), then just navigate back to the devices screen.
                val disconnectOrBack = {
                    if (!controller.disconnect()) {
                        navController.navigateUp()
                    }
                }

                Scanner(controller.currentDevice, disconnectOrBack) {
                    scope.launch {
                        controller.sendString(it)
                    }
                }

                BackHandler(onBack = disconnectOrBack)
            }

            composable(Routes.Settings) {
                Settings()

                BackHandler {
                    if (!navController.navigateUp())
                        navController.navigate(Routes.Devices)
                }
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
                } else {
                    navController.popBackStack(Routes.Devices, inclusive = false)
                }
            }
        }

        onDispose {
            controller.unregisterListener(listener)
        }
    }
}

// based on soup.compose.material.motion.animation.materialSharedAxisXIn
fun inAnimation(forward: Boolean, slideDistance: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        initialOffsetX = { if (forward) slideDistance else -slideDistance }
    ) + fadeIn(tween(210, 90, LinearOutSlowInEasing))

// based on soup.compose.material.motion.animation.materialSharedAxisXOut
fun outAnimation(forward: Boolean, slideDistance: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        targetOffsetX = { if (forward) -slideDistance else slideDistance }
    ) + fadeOut(tween(90, 0, FastOutLinearInEasing))

