package dev.fabik.bluetoothhid

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.BluetoothService
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference

val LocalController = staticCompositionLocalOf<BluetoothController> {
    error("No BluetoothController provided")
}

class MainActivity : ComponentActivity() {

    private var bluetoothController: BluetoothController? by mutableStateOf(null)

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BluetoothService.LocalBinder
            bluetoothController = binder?.getController()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothController = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothHIDTheme {
                Surface(Modifier.fillMaxSize()) {
                    val allowScreenRotation by rememberPreference(PreferenceStore.ALLOW_SCREEN_ROTATION)

                    LaunchedEffect(allowScreenRotation) {
                        requestedOrientation = if (allowScreenRotation) {
                            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
                        }
                    }

                    RequiresBluetoothPermission {
                        bluetoothController?.let {
                            CompositionLocalProvider(LocalController provides it) {
                                NavGraph()
                            }
                        }

                        ComposableLifecycle(LocalLifecycleOwner.current) { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_CREATE -> {
                                    // Start and bind bluetooth service
                                    Intent(this@MainActivity, BluetoothService::class.java).let {
                                        startForegroundService(it)
                                        bindService(it, serviceConnection, BIND_AUTO_CREATE)
                                    }
                                }

                                Lifecycle.Event.ON_DESTROY -> {
                                    // Don't stop service if activity is being recreated due to a configuration change
                                    if (!isChangingConfigurations) {
                                        // Unbind and stop bluetooth service
                                        unbindService(serviceConnection)
                                        stopService(
                                            Intent(
                                                this@MainActivity,
                                                BluetoothService::class.java
                                            )
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
