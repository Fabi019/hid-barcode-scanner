package dev.fabik.bluetoothhid.bt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dev.fabik.bluetoothhid.MainActivity
import dev.fabik.bluetoothhid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BluetoothService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_hid_service"

        const val ACTION_REGISTER = "register"
        const val ACTION_STOP = "stop"
    }

    private val binder = LocalBinder()
    private lateinit var controller: BluetoothController

    inner class LocalBinder : Binder() {
        fun getController(): BluetoothController = controller
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        controller = BluetoothController(this)

        // Register controller once when service is created
        CoroutineScope(Dispatchers.IO).launch {
            controller.register()
        }
    }

    override fun onDestroy() {
        controller.unregister()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Debug actions to stop service and register controller again
        if (intent?.action == ACTION_REGISTER) {
            runBlocking {
                controller.register()
            }
        } else if (intent?.action == ACTION_STOP) {
            controller.unregister()
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

        startForeground(1, notification)

        return START_STICKY
    }

}
