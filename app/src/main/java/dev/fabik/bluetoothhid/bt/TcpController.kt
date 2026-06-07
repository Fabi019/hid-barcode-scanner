package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.util.Log
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class TcpController(private val context: Context) {
    companion object {
        private const val TAG = "TcpController"
    }

    private fun L(msg: String) = Log.i(TAG, msg)
    private fun LE(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Active clients in server mode
    private val connectedClients = CopyOnWriteArrayList<Socket>()

    // Active socket in client mode
    private var activeSocket: Socket? = null
    @Volatile private var isConnected: Boolean = false

    // Server state
    private var serverSocket: ServerSocket? = null
    @Volatile private var isServerStarted: Boolean = false
    private var serverJob: Job? = null

    // Client state
    private var clientJob: Job? = null

    private var listeningStateCallback: ((Boolean) -> Unit)? = null
    private var connectedStateCallback: ((Boolean) -> Unit)? = null

    fun setListeningStateCallback(callback: (Boolean) -> Unit) {
        listeningStateCallback = callback
    }

    fun setConnectedStateCallback(callback: (Boolean) -> Unit) {
        connectedStateCallback = callback
    }

    // "Listening" = TCP loop is running but no active connection yet
    fun isListening(): Boolean = !isConnected && (serverJob?.isActive == true || clientJob?.isActive == true)

    private fun notifyListeningState() {
        listeningStateCallback?.invoke(isListening())
        connectedStateCallback?.invoke(isConnected)
    }

    fun startServer() {
        if (serverJob?.isActive == true) return
        clientJob?.cancel()
        clientJob = null

        L("Starting TCP server")
        serverJob = controllerScope.launch {
            var errorCount = 0
            while (isActive) {
                try {
                    val port = context.getPreference(PreferenceStore.TCP_SERVER_PORT).first()
                        .toIntOrNull()?.coerceIn(1, 65535) ?: 9100
                    val maxClients = context.getPreference(PreferenceStore.TCP_SERVER_MAX_CLIENTS).first()
                        .coerceIn(1, 10)

                    if (!isServerStarted || serverSocket == null) {
                        // SO_REUSEADDR lets us rebind immediately after a connection closes
                        // without waiting for the OS TIME_WAIT period to expire
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(java.net.InetSocketAddress(port))
                        }
                        isServerStarted = true
                        L("TCP server listening on port $port (max $maxClients clients)")
                        notifyListeningState()
                        errorCount = 0
                    }

                    // Accept client (blocking)
                    val socket = serverSocket?.accept() ?: continue
                    val clientAddr = socket.inetAddress.hostAddress ?: socket.inetAddress.toString()

                    if (connectedClients.size >= maxClients) {
                        L("TCP max clients ($maxClients) reached, rejecting $clientAddr")
                        runCatching { socket.close() }
                        continue
                    }

                    L("TCP client connected from $clientAddr (${connectedClients.size + 1}/$maxClients)")
                    connectedClients.add(socket)
                    isConnected = true
                    notifyListeningState()

                    // Handle each client in a separate coroutine; server keeps accepting
                    launch(Dispatchers.IO) {
                        manageClientConnection(socket, clientAddr, maxClients)
                    }

                } catch (e: IOException) {
                    if (!isActive) break
                    errorCount++
                    LE("TCP server error (attempt $errorCount)", e)
                    serverSocket?.close()
                    serverSocket = null
                    isServerStarted = false
                    connectedClients.forEach { runCatching { it.close() } }
                    connectedClients.clear()
                    isConnected = false
                    notifyListeningState()
                    val backoff = minOf(250L * errorCount, 5000L) + Random.nextLong(0, 200)
                    delay(backoff)
                }
            }
            // Cleanup on coroutine cancellation
            serverSocket?.close()
            serverSocket = null
            isServerStarted = false
            connectedClients.forEach { runCatching { it.close() } }
            connectedClients.clear()
            isConnected = false
            notifyListeningState()
            L("TCP server loop terminated")
        }
    }

    private fun manageClientConnection(socket: Socket, clientAddr: String, maxClients: Int) {
        val input = socket.getInputStream()
        L("TCP server: connection active from $clientAddr")
        try {
            val buffer = ByteArray(1024)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                L("TCP server received from $clientAddr: ${String(buffer, 0, bytes)}")
            }
        } catch (e: IOException) {
            LE("TCP server: connection error from $clientAddr", e)
        } finally {
            runCatching { socket.close() }
            connectedClients.remove(socket)
            isConnected = connectedClients.isNotEmpty()
            notifyListeningState()
            L("TCP server: $clientAddr disconnected (${connectedClients.size}/$maxClients remaining)")
        }
    }

    fun startClient() {
        if (clientJob?.isActive == true) return
        serverJob?.cancel()
        serverJob = null
        serverSocket?.close()
        serverSocket = null
        isServerStarted = false
        connectedClients.forEach { runCatching { it.close() } }
        connectedClients.clear()

        L("Starting TCP client")
        clientJob = controllerScope.launch {
            var errorCount = 0
            while (isActive) {
                try {
                    val host = context.getPreference(PreferenceStore.TCP_CLIENT_HOST).first()
                    val port = context.getPreference(PreferenceStore.TCP_CLIENT_PORT).first()
                        .toIntOrNull()?.coerceIn(1, 65535) ?: 9100

                    if (host.isBlank()) {
                        L("TCP client: no host configured, waiting...")
                        notifyListeningState()
                        delay(5000)
                        continue
                    }

                    L("TCP client connecting to $host:$port")
                    notifyListeningState()

                    val socket = Socket(host, port)
                    L("TCP client connected to $host:$port")

                    activeSocket = socket
                    isConnected = true
                    errorCount = 0
                    notifyListeningState()

                    withContext(Dispatchers.IO) {
                        manageConnection(socket, "client")
                    }

                } catch (e: IOException) {
                    errorCount++
                    LE("TCP client connect error (attempt $errorCount)", e)
                    val backoff = minOf(250L * errorCount, 5000L) + Random.nextLong(0, 200)
                    delay(backoff)
                } finally {
                    isConnected = false
                    runCatching { activeSocket?.close() }
                    activeSocket = null
                    notifyListeningState()
                }
            }
            L("TCP client loop terminated")
        }
    }

    fun stop() {
        L("Stopping TCP controller")

        serverJob?.cancel()
        serverJob = null
        clientJob?.cancel()
        clientJob = null

        runCatching { activeSocket?.close() }
        activeSocket = null

        connectedClients.forEach { runCatching { it.close() } }
        connectedClients.clear()
        isConnected = false

        runCatching { serverSocket?.close() }
        serverSocket = null
        isServerStarted = false

        notifyListeningState()
        L("TCP controller stopped")
    }

    private fun manageConnection(socket: Socket, role: String) {
        val input = socket.getInputStream()
        L("TCP $role: connection active")
        try {
            val buffer = ByteArray(1024)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                L("TCP $role received: ${String(buffer, 0, bytes)}")
            }
        } catch (e: IOException) {
            LE("TCP $role: connection error", e)
        } finally {
            runCatching { socket.close() }
            L("TCP $role: connection terminated")
        }
    }

    fun sendProcessedData(processedString: String) {
        // Server mode: broadcast to all connected clients
        if (connectedClients.isNotEmpty()) {
            val data = processedString.toByteArray(Charsets.UTF_8)
            connectedClients.forEach { socket ->
                runCatching {
                    socket.getOutputStream().write(data)
                }.onFailure { e ->
                    LE("TCP broadcast send failed to ${socket.inetAddress.hostAddress ?: socket.inetAddress}", e)
                }
            }
            L("TCP broadcast to ${connectedClients.size} client(s): $processedString")
            return
        }
        // Client mode: single socket
        val socket = activeSocket
        if (socket == null || !isConnected) {
            L("TCP: no active connection — data dropped")
            return
        }
        runCatching {
            socket.getOutputStream().write(processedString.toByteArray(Charsets.UTF_8))
            L("TCP sent: $processedString")
        }.onFailure { e ->
            LE("TCP send failed", e)
        }
    }
}
