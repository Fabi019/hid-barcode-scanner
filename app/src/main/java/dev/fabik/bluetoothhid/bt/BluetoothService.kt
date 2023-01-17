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
    }

    private val binder = LocalBinder()
    private lateinit var controller: BluetoothController

    inner class LocalBinder : Binder() {
        fun getController(): BluetoothController = controller
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        controller = BluetoothController(this)
    }

    override fun onDestroy() {
        controller.disconnect()
        controller.unregister()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            "Bluetooth HID Service",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth HID Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
        notification.flags = Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT

        startForeground(1, notification)

        return START_STICKY
    }

}
