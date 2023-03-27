package dev.fabik.bluetoothhid.bt

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dev.fabik.bluetoothhid.MainActivity
import dev.fabik.bluetoothhid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothService : Service() {

    companion object {
        private const val CHANNEL_ID = "bt_hid_service"
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
    }

    override fun onDestroy() {
        controller.unregister()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.unregister()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        CoroutineScope(Dispatchers.IO).launch {
            controller.register()
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
