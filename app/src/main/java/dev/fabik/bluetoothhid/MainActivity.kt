package dev.fabik.bluetoothhid

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.bt.BluetoothService
import dev.fabik.bluetoothhid.ui.NavGraph
import dev.fabik.bluetoothhid.ui.RequiresBluetoothPermission
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.utils.ComposableLifecycle

class MainActivity : ComponentActivity() {

    private var bluetoothService: BluetoothService.LocalBinder? = null
    private var bluetoothController: BluetoothController? by mutableStateOf(null)

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = (service as BluetoothService.LocalBinder)
            bluetoothService = binder
            bluetoothController = binder.getController()

            Toast.makeText(
                this@MainActivity,
                getText(R.string.bt_service_connected),
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            bluetoothController = null

            Toast.makeText(
                this@MainActivity,
                getText(R.string.bt_service_disconnected),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothHIDTheme {
                Surface(Modifier.fillMaxSize()) {
                    RequiresBluetoothPermission {
                        bluetoothController?.let {
                            NavGraph(it)
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
                                    // Unbind and stop bluetooth service
                                    unbindService(serviceConnection)
                                    stopService(
                                        Intent(
                                            this@MainActivity,
                                            BluetoothService::class.java
                                        )
                                    )
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
