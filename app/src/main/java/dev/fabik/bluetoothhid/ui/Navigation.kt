package dev.fabik.bluetoothhid.ui

import android.app.Activity
import android.bluetooth.BluetoothProfile
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavGraph(controller: BluetoothController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val navController = rememberAnimatedNavController()

    val slideDistance = LocalDensity.current.run { 30.dp.roundToPx() }

    AnimatedNavHost(
        navController,
        startDestination = Routes.Devices,
        enterTransition = { inAnimation(true, slideDistance) },
        exitTransition = { outAnimation(true, slideDistance) },
        popEnterTransition = { inAnimation(false, slideDistance) },
        popExitTransition = { outAnimation(false, slideDistance) }
    ) {
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

            Scanner(navController, controller.currentDevice, disconnectOrBack) {
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
                    navController.popBackStack(Routes.Main, inclusive = true)
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

