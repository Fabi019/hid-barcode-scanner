package dev.fabik.bluetoothhid.bt

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import dev.fabik.bluetoothhid.MainActivity
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.output.ChannelEvent
import dev.fabik.bluetoothhid.output.OutputManager
import dev.fabik.bluetoothhid.output.OutputSelection
import dev.fabik.bluetoothhid.output.external.ExternalChannel
import dev.fabik.bluetoothhid.output.external.ExternalController
import dev.fabik.bluetoothhid.output.hid.HidChannel
import dev.fabik.bluetoothhid.output.rfcomm.RfcommChannel
import dev.fabik.bluetoothhid.output.rfcomm.RfcommController
import dev.fabik.bluetoothhid.utils.ComposableLifecycle
import dev.fabik.bluetoothhid.utils.ConnectionMode
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class BluetoothService : Service() {

    companion object {
        private const val TAG = "BTService"
        private const val CHANNEL_ID = "bt_hid_service"

        const val ACTION_REGISTER = "register"
        const val ACTION_STOP = "stop"
    }

    private val binder = LocalBinder()
    private var outputManager: OutputManager? = null
    private lateinit var serviceScope: CoroutineScope

    inner class LocalBinder : Binder() {
        fun getOutputManager(): OutputManager? = outputManager
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        val channels = mapOf(
            ConnectionMode.HID to HidChannel(this, adapter, KeyTranslator(this), serviceScope),
            ConnectionMode.RFCOMM to RfcommChannel(
                this, RfcommController(this, adapter), serviceScope
            ),
            ConnectionMode.EXTERNAL to ExternalChannel(
                this,
                ExternalController(this),
                scannerId = { adapter.name },
            ),
        )

        // Primary mode from the CONNECTION_MODE preference; the ENABLE_EXTERNAL_OUTPUT toggle
        // additionally runs the external channel as a parallel mirror in HID/RFCOMM mode
        // (in EXTERNAL mode external output is implied/always on — no mirror needed).
        val selection = getPreferences(
            PreferenceStore.CONNECTION_MODE,
            PreferenceStore.ENABLE_EXTERNAL_OUTPUT
        ).map {
            val primary = PreferenceStore.CONNECTION_MODE.extractEnum(it)
            val mirrors =
                if (primary != ConnectionMode.EXTERNAL &&
                    PreferenceStore.ENABLE_EXTERNAL_OUTPUT.extract(it)
                ) setOf(ConnectionMode.EXTERNAL) else emptySet()
            OutputSelection(primary, mirrors)
        }

        val manager = OutputManager(channels, selection, serviceScope)
        outputManager = manager

        // Surface channel notices (proxy connected/disconnected, registration errors) as toasts —
        // same feedback the old controller provided directly.
        serviceScope.launch {
            manager.events.collect { event ->
                if (event is ChannelEvent.Notice) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, getString(event.res), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runBlocking { outputManager?.stop() }
        outputManager = null
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Debug actions to stop the service and restart the primary channel
        if (intent?.action == ACTION_REGISTER) {
            serviceScope.launch { outputManager?.restartPrimary() }
        } else if (intent?.action == ACTION_STOP) {
            runBlocking { outputManager?.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val pendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bt_hid_service),
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bt_hid_service))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
        notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Catch ForegroundServiceStartNotAllowedException when app is in background
            runCatching {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            }.onFailure {
                Log.e(TAG, "Error starting foreground service!", it)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            startForeground(1, notification)
        }

        return START_STICKY
    }

}

@Composable
fun rememberBluetoothControllerService(
    context: Context,
    startStop: Boolean = true
): BluetoothService.LocalBinder? {
    val serviceBinder = remember { mutableStateOf<BluetoothService.LocalBinder?>(null) }
    val intent = remember { Intent(context, BluetoothService::class.java) }

    DisposableEffect(Unit) {
        val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    serviceBinder.value = service as BluetoothService.LocalBinder?
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceBinder.value = null
                }
            }

        context.bindService(
            intent,
            serviceConnection,
            if (startStop) Activity.BIND_AUTO_CREATE else 0
        )

        onDispose {
            context.unbindService(serviceConnection)
            serviceBinder.value = null
        }
    }

    if (startStop) {
        ComposableLifecycle { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    // Catch ForegroundServiceStartNotAllowedException when app is in background
                    runCatching {
                        context.startForegroundService(intent)
                    }.onFailure {
                        Log.e("BTService", "Failed to start service", it)
                    }

                Lifecycle.Event.ON_DESTROY -> {
                    if ((context as? Activity)?.isChangingConfigurations == false) {
                        context.stopService(intent)
                    }
                }

                else -> {}
            }
        }
    }

    return serviceBinder.value
}
