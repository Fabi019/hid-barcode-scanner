package dev.fabik.bluetoothhid.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.widget.Toast
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class RfcommController(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {
    companion object {
        private const val TAG = "RfcommController"
        private val RFCOMM_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // ****************************************
    // RFCOMM implementation code
    // ****************************************

    var connectionMode: Int = 0
    private var rfcSocket: BluetoothSocket? = null // RFCOMM Client
    private var serverSocket: BluetoothServerSocket? = null  // RFCOMM Server
    private var isRFCOMMconnected: Boolean = false
    private var isRFCOMMServerStarted: Boolean = false

    fun connectRFCOMM(){
        CoroutineScope(Dispatchers.IO).launch {
            connectionMode = context.getPreference(PreferenceStore.CONNECTION_MODE).first()

            if (connectionMode == 1) {
                Log.i(TAG, "Connection Mode: RFCOMM")
                startRFCOMMServer()
            }
            else
            {
                Log.i(TAG, "Connection Mode: HID")
            }
        }
    }

    fun disconnectRFCOMM()
    {
        if (connectionMode == 1)
        {
            // closing client and server sockets if they are not null
            rfcSocket?.close()
            isRFCOMMconnected = false

            serverSocket?.close()
            isRFCOMMServerStarted = false
        }
    }

    private fun reconnectRFCOMM() {
        Log.i(TAG, "Reconnecting...")

        disconnectRFCOMM()
        connectRFCOMM()
    }

    private fun startRFCOMMServer() {
        // Run RFCOMM server in background with coroutine
        CoroutineScope(Dispatchers.IO).launch {

            if (!isRFCOMMServerStarted)
            {
                // Open BluetoothServerSocket for RFCOMM connections (SPP)
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("Barcode Scanner", RFCOMM_UUID)
                Log.i(TAG, "Server: Server Started! Waiting for connections...")

                isRFCOMMServerStarted = true
            }
            else
            {
                Log.i(TAG, "Server: Server already running! Waiting for connections...")
            }

            try {
                // Accept connections - blocks to the moment of connection
                rfcSocket = serverSocket?.accept()
                Log.i(TAG, "Server: Client connected via RFCOMM")

                // Check if rfcSocket is not null
                rfcSocket?.let { socket ->
                    withContext(Dispatchers.IO) {
                        isRFCOMMconnected = true
                        manageRFCOMMConnection(socket)
                    }
                } ?: Log.e(TAG, "Socket: Client socket is null")
            } catch (e: IOException) {
                Log.e(TAG, "Server: Error starting server", e)
            }
        }
    }

    private fun manageRFCOMMConnection(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        Log.i(TAG, "Socket: Opened!")

        try {
            // Welcome message to the client
            val message = "Hello from Android SPP Server implemented in HID Bluetooth Scanner".toByteArray()
            outputStream.write(message)

            // Read data from the client
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                bytes = inputStream.read(buffer)
                val readMessage = String(buffer, 0, bytes)
                Log.d(TAG, "Socket - Data Received from Client: $readMessage")
                showReceivedMessage(readMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Socket: Disconnected or communication error", e)
        } finally {
            try {
                socket.close()
                Log.i(TAG, "Socket: Closed! -> Client disconnected")
            } catch (closeException: IOException) {
                Log.e(TAG, "Socket: Error closing socket!", closeException)
            }
        }
    }

    fun showReceivedMessage(message: String){
        CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG
        ).show()
        }
    }

    fun sendProcessedData(processedString: String) {
        // Convert the processed string to UTF-8 byte array
        val messageBytes = processedString.toByteArray(Charsets.UTF_8)

        // Send data via RFCOMM
        rfcSocket?.let { socket ->
            try {
                Log.i(TAG, "Socket - Data Sent to Client: $processedString")
                socket.outputStream.write(messageBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Socket: Error sending data", e)
                reconnectRFCOMM()
            }
        } ?: run {
            Log.e(TAG, "Socket: socket is null!")
            // Trigger reconnection if the socket is null
            reconnectRFCOMM()
        }
    }
}
