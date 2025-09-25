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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

@SuppressLint("MissingPermission")
class RfcommController(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {
    companion object {
        private const val TAG = "RfcommController"
        private val RFCOMM_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var listeningStateCallback: ((Boolean) -> Unit)? = null

    private fun L(msg: String) = Log.i("$TAG[$sessionId]", msg)
    private fun LE(msg: String, t: Throwable? = null) = Log.e("$TAG[$sessionId]", msg, t)

    fun setExpectedDeviceAddress(addr: String?) {
        expectedDeviceAddress = addr
        L("Expected device address set to: ${addr ?: "any"}")
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        autoConnectEnabled = enabled
        L("AutoConnect ${if (enabled) "enabled" else "disabled"}")

        if (enabled && !isRFCOMMconnected && !isRFCOMMServerStarted) {
            // Try to auto-connect to last known device
            lastConnectedDeviceAddress?.let { addr ->
                L("Attempting auto-connect to last device: $addr")
                expectedDeviceAddress = addr
                controllerScope.launch {
                    connectRFCOMM()
                }
            }
        }
    }

    // ****************************************
    // RFCOMM implementation code
    // ****************************************

    private var rfcSocket: BluetoothSocket? = null // RFCOMM Client
    private var serverSocket: BluetoothServerSocket? = null  // RFCOMM Server
    @Volatile private var isRFCOMMconnected: Boolean = false
    @Volatile private var isRFCOMMServerStarted: Boolean = false
    private var acceptJob: Job? = null
    @Volatile private var expectedDeviceAddress: String? = null
    @Volatile private var autoConnectEnabled: Boolean = false
    @Volatile private var lastConnectedDeviceAddress: String? = null
    private val reconnectMutex = Mutex()
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionId = System.currentTimeMillis().toString(16)

    fun setListeningStateCallback(callback: (Boolean) -> Unit) {
        listeningStateCallback = callback
    }

    /**
     * Returns true if the RFCOMM server is actively listening for new connections.
     * This means the server is started but no client is currently connected.
     * When a client connects, the server pauses listening and this returns false.
     */
    fun isListening(): Boolean = isRFCOMMServerStarted && !isRFCOMMconnected

    private fun notifyListeningState() {
        val listening = isListening()
        listeningStateCallback?.invoke(listening)
    }

    fun connectRFCOMM(){
        controllerScope.launch {
            L("Starting RFCOMM connection")
            startRFCOMMServer()
        }
    }

    fun disconnectRFCOMM() {
        L("Disconnecting RFCOMM - cleaning up all resources")

        // Always cleanup regardless of connection mode to prevent zombie servers
        runCatching { rfcSocket?.close() }.onFailure { LE("Error closing client socket", it) }
        rfcSocket = null
        isRFCOMMconnected = false

        runCatching { serverSocket?.close() }.onFailure { LE("Error closing server socket", it) }
        serverSocket = null
        isRFCOMMServerStarted = false

        acceptJob?.cancel()
        acceptJob = null

        notifyListeningState() // Notify that we're no longer listening

        L("RFCOMM cleanup completed")
    }

    private suspend fun reconnectRFCOMM() = reconnectMutex.withLock {
        L("Attempting RFCOMM reconnection")
        if (isRFCOMMconnected) {
            L("Already connected - skipping reconnect")
            return@withLock
        }

        disconnectRFCOMM()
        delay(100) // Brief pause before reconnecting
        connectRFCOMM()
    }

    @Synchronized
    private fun startRFCOMMServer() {
        if (acceptJob?.isActive == true) {
            L("Server already accepting connections - skipping")
            return
        }

        L("Starting RFCOMM server with accept loop")
        acceptJob = controllerScope.launch {
            var errorCount = 0
            while (isActive) {
                try {
                    // Create server socket if needed
                    if (!isRFCOMMServerStarted || serverSocket == null) {
                        val useInsecure = context.getPreference(PreferenceStore.INSECURE_RFCOMM).first()

                        try {
                            serverSocket = if (useInsecure) {
                                L("Creating insecure RFCOMM server socket")
                                bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                                    "Barcode Scanner", RFCOMM_UUID
                                )
                            } else {
                                L("Creating secure RFCOMM server socket")
                                bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                                    "Barcode Scanner", RFCOMM_UUID
                                )
                            }
                            isRFCOMMServerStarted = true
                            L("Server listening for connections (${if (useInsecure) "insecure" else "secure"})...")
                            notifyListeningState()
                            errorCount = 0 // Reset error count on successful socket creation
                        } catch (e: IOException) {
                            if (!useInsecure) {
                                L("Secure RFCOMM failed, trying insecure fallback")
                                try {
                                    serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                                        "Barcode Scanner", RFCOMM_UUID
                                    )
                                    isRFCOMMServerStarted = true
                                    L("Server listening for connections (insecure fallback)...")
                                    notifyListeningState()
                                    errorCount = 0
                                } catch (fallbackException: IOException) {
                                    LE("Both secure and insecure RFCOMM failed", fallbackException)
                                    throw fallbackException
                                }
                            } else {
                                LE("Insecure RFCOMM creation failed", e)
                                throw e
                            }
                        }
                    }

                    // Accept client connection (blocking call)
                    val socket = serverSocket?.accept() ?: continue
                    val clientAddress = socket.remoteDevice?.address

                    L("Client connection attempt from: $clientAddress")

                    // Check if we already have an active client
                    if (isRFCOMMconnected) {
                        L("Rejecting connection - already serving client")
                        runCatching { socket.close() }
                        continue
                    }

                    // Filter by expected device address if set
                    val expectedAddr = expectedDeviceAddress
                    if (expectedAddr != null && clientAddress != expectedAddr) {
                        L("Rejecting unexpected client $clientAddress (expected $expectedAddr)")
                        runCatching { socket.close() }
                        continue
                    }

                    L("Accepting client connection from $clientAddress")

                    // Remember this device for auto-connect
                    lastConnectedDeviceAddress = clientAddress

                    // Pause listening while serving this client
                    runCatching { serverSocket?.close() }
                    serverSocket = null
                    isRFCOMMServerStarted = false

                    // Set up connection state
                    rfcSocket = socket
                    isRFCOMMconnected = true
                    notifyListeningState() // Notify that we're no longer listening

                    // Handle the connection (blocking until disconnect)
                    withContext(Dispatchers.IO) {
                        manageRFCOMMConnection(socket)
                    }

                } catch (e: IOException) {
                    errorCount++
                    LE("Server accept failed (attempt $errorCount)", e)

                    // Clean up server socket on error
                    runCatching { serverSocket?.close() }
                    serverSocket = null
                    isRFCOMMServerStarted = false

                    // Progressive backoff with jitter
                    val baseDelay = minOf(250L * errorCount, 5000L)
                    val jitter = Random.nextLong(0, 200)
                    delay(baseDelay + jitter)

                } finally {
                    // Always clean up client state after connection ends
                    isRFCOMMconnected = false
                    runCatching { rfcSocket?.close() }
                    rfcSocket = null
                    notifyListeningState() // Notify state change

                    // Auto-reconnect if enabled and we were connected to a specific device
                    if (autoConnectEnabled && lastConnectedDeviceAddress != null) {
                        L("Connection ended - scheduling auto-reconnect in 2 seconds")
                        delay(2000) // Brief delay before attempting reconnect
                        if (!isRFCOMMconnected) {
                            L("Attempting auto-reconnect to ${lastConnectedDeviceAddress}")
                            expectedDeviceAddress = lastConnectedDeviceAddress
                            // Server will restart in next loop iteration
                        }
                    }
                }
            }
            L("Accept loop terminated")
        }
    }

    private fun manageRFCOMMConnection(socket: BluetoothSocket) {
        val inputStream = socket.inputStream
        val outputStream = socket.outputStream

        L("Socket opened for client connection")

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
                L("Data received from client: $readMessage")
                showReceivedMessage(readMessage)
            }
        } catch (e: IOException) {
            LE("Socket disconnected or communication error", e)
        } finally {
            runCatching { socket.close() }.onFailure {
                LE("Error closing client socket", it)
            }
            // State cleanup is handled in startRFCOMMServer finally block
            L("Client connection terminated")
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
        val messageBytes = processedString.toByteArray(Charsets.UTF_8)
        val socket = rfcSocket

        if (socket == null || !isRFCOMMconnected) {
            L("No active connection - triggering reconnect")
            controllerScope.launch {
                reconnectRFCOMM()
                delay(200) // Wait for reconnection

                // Single retry attempt
                rfcSocket?.let { retrySocket ->
                    runCatching {
                        retrySocket.outputStream.write(messageBytes)
                        L("Data sent after reconnect: $processedString")
                    }.onFailure {
                        LE("Retry send failed", it)
                    }
                } ?: L("Reconnection failed - no socket available")
            }
            return
        }

        runCatching {
            socket.outputStream.write(messageBytes)
            L("Data sent: $processedString")
        }.onFailure { e ->
            LE("Send failed", e)
            controllerScope.launch { reconnectRFCOMM() }
        }
    }
}
