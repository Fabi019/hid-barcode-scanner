package dev.fabik.bluetoothhid.bt

import android.content.Context
import android.util.Log
import android.widget.Toast
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
import dev.fabik.bluetoothhid.utils.localIpAddresses
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

data class TcpStatusData(
    val serverAddresses: List<String> = emptyList(),
    val clientAddresses: List<String> = emptyList(),
    val clientTarget: String? = null,
) {
    val isEmpty get() = serverAddresses.isEmpty() && clientAddresses.isEmpty() && clientTarget == null
}

class TcpController(private val context: Context) {
    companion object {
        private const val TAG = "TcpController"

        // Sent to the peer right after a connection is established, mirroring RFCOMM.
        // Keeps the protocol intentionally trivial — just enough to verify send/receive
        // works end-to-end. May be removed from both TCP and RFCOMM later.
        private const val WELCOME_MESSAGE =
            "Hello from Android HID Barcode Scanner (TCP)"
    }

    private fun L(msg: String) = Log.i(TAG, msg)
    private fun LE(msg: String, t: Throwable? = null) = Log.e(TAG, msg, t)

    // Mirrors RfcommController.showReceivedMessage — surface data received from the peer
    // as a Toast so both transports behave identically.
    private fun showReceivedMessage(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
    @Volatile private var serverEpoch = 0
    @Volatile private var serverPort: Int? = null

    // Client state
    private var clientJob: Job? = null
    @Volatile private var clientTarget: String? = null

    private var listeningStateCallback: ((Boolean) -> Unit)? = null
    private var connectedStateCallback: ((Boolean) -> Unit)? = null
    private var connectedAddressesCallback: ((TcpStatusData) -> Unit)? = null

    fun setListeningStateCallback(callback: (Boolean) -> Unit) {
        listeningStateCallback = callback
    }

    fun setConnectedStateCallback(callback: (Boolean) -> Unit) {
        connectedStateCallback = callback
    }

    fun setConnectedAddressesCallback(callback: (TcpStatusData) -> Unit) {
        connectedAddressesCallback = callback
    }

    // "Listening" semantics differ per mode:
    //  - Server: the accept loop is running (still accepting clients, even if some are
    //    already connected) — not "no client yet", which was RFCOMM single-client logic.
    //  - Client: the loop is running but no connection is established yet (connecting).
    fun isListening(): Boolean {
        val serverListening = serverJob?.isActive == true && isServerStarted
        val clientConnecting = clientJob?.isActive == true && !isConnected
        return serverListening || clientConnecting
    }

    private fun notifyListeningState() {
        listeningStateCallback?.invoke(isListening())
        connectedStateCallback?.invoke(isConnected)
        val data = when {
            isServerStarted -> serverPort?.let { port ->
                val localAddrs = localIpAddresses()
                val serverAddrs = if (localAddrs.isNotEmpty())
                    localAddrs.map { "$it:$port" }
                else listOf(":$port")
                val clientAddrs = connectedClients.mapNotNull { it.inetAddress?.hostAddress }
                TcpStatusData(serverAddresses = serverAddrs, clientAddresses = clientAddrs)
            } ?: TcpStatusData()
            clientJob?.isActive == true -> {
                val target = if (isConnected && activeSocket != null)
                    activeSocket!!.run { "${inetAddress?.hostAddress ?: inetAddress}:$port" }
                else clientTarget
                TcpStatusData(clientTarget = target)
            }
            else -> TcpStatusData()
        }
        connectedAddressesCallback?.invoke(data)
    }

    fun startServer() {
        if (serverJob?.isActive == true) return
        clientJob?.cancel()
        clientJob = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        isConnected = false
        clientTarget = null
        notifyListeningState()

        L("Starting TCP server")
        serverJob = controllerScope.launch {
            val epoch = ++serverEpoch
            var errorCount = 0
            while (isActive) {
                try {
                    val port = context.getPreference(PreferenceStore.TCP_SERVER_PORT).first()
                        .toIntOrNull()?.coerceIn(1, 65535) ?: 51000
                    val maxClients = context.getPreference(PreferenceStore.TCP_SERVER_MAX_CLIENTS).first()
                        .coerceIn(1, 10)
                    val idleTimeoutMs = context.getPreference(PreferenceStore.TCP_SERVER_CLIENT_IDLE_TIMEOUT_MS).first()
                        .coerceIn(0, 300_000)

                    if (!isServerStarted || serverSocket == null) {
                        // SO_REUSEADDR lets us rebind immediately after a connection closes
                        // without waiting for the OS TIME_WAIT period to expire
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(java.net.InetSocketAddress(port))
                        }
                        serverPort = port
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
                        manageClientConnection(socket, clientAddr, maxClients, idleTimeoutMs)
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
            // Cleanup on coroutine cancellation — skip if a newer epoch has taken over
            if (epoch == serverEpoch) {
                serverSocket?.close()
                serverSocket = null
                isServerStarted = false
                connectedClients.forEach { runCatching { it.close() } }
                connectedClients.clear()
                isConnected = false
                notifyListeningState()
            }
            L("TCP server loop terminated")
        }
    }

    private fun manageClientConnection(socket: Socket, clientAddr: String, maxClients: Int, idleTimeoutMs: Int) {
        if (idleTimeoutMs > 0) socket.soTimeout = idleTimeoutMs
        val input = socket.getInputStream()
        L("TCP server: connection active from $clientAddr (idle timeout: ${if (idleTimeoutMs > 0) "${idleTimeoutMs}ms" else "disabled"})")
        try {
            // Welcome message to the client (mirrors RFCOMM) — lets you verify the protocol
            runCatching { socket.getOutputStream().apply { write(WELCOME_MESSAGE.toByteArray()); flush() } }
            val buffer = ByteArray(1024)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                val received = String(buffer, 0, bytes)
                L("TCP server received from $clientAddr: $received")
                showReceivedMessage(received)
            }
        } catch (e: java.net.SocketTimeoutException) {
            L("TCP server: $clientAddr idle timeout after ${idleTimeoutMs}ms — disconnecting")
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
        serverPort = null
        connectedClients.forEach { runCatching { it.close() } }
        connectedClients.clear()
        isConnected = false
        notifyListeningState()

        L("Starting TCP client")
        clientJob = controllerScope.launch {
            var errorCount = 0
            while (isActive) {
                var socket: Socket? = null
                try {
                    val host = context.getPreference(PreferenceStore.TCP_CLIENT_HOST).first()
                    val port = context.getPreference(PreferenceStore.TCP_CLIENT_PORT).first()
                        .toIntOrNull()?.coerceIn(1, 65535) ?: 51000
                    val connectTimeoutMs = context.getPreference(PreferenceStore.TCP_CLIENT_CONNECT_TIMEOUT_MS).first()
                        .coerceIn(500, 30_000)

                    if (host.isBlank()) {
                        L("TCP client: no host configured, waiting...")
                        clientTarget = null
                        notifyListeningState()
                        delay(5000)
                        continue
                    }

                    clientTarget = "$host:$port"
                    L("TCP client connecting to $host:$port")
                    notifyListeningState()

                    socket = Socket()
                    activeSocket = socket  // pre-assign so stop() can close it during connect
                    socket.connect(java.net.InetSocketAddress(host, port), connectTimeoutMs)
                    L("TCP client connected to $host:$port")

                    isConnected = true
                    errorCount = 0
                    notifyListeningState()

                    withContext(Dispatchers.IO) {
                        manageConnection(socket, "client")
                    }

                    // Clean disconnect (read returned -1): manageConnection swallows IOException,
                    // so the catch-block backoff below never runs for drops after a successful
                    // connect. Without a delay here, a server that accepts then immediately closes
                    // forces a tight reconnect loop. Apply a fixed minimal backoff with jitter.
                    if (isActive) {
                        val backoff = 1000L + Random.nextLong(0, 200)
                        L("TCP client disconnected — reconnecting in ${backoff}ms")
                        delay(backoff)
                    }

                } catch (e: IOException) {
                    errorCount++
                    LE("TCP client connect error (attempt $errorCount)", e)
                    val backoff = minOf(250L * errorCount, 5000L) + Random.nextLong(0, 200)
                    delay(backoff)
                } finally {
                    isConnected = false
                    // Only close/null if this iteration still owns activeSocket — prevents
                    // clobbering a new socket assigned by restartClient() during teardown
                    if (activeSocket === socket) {
                        runCatching { socket?.close() }
                        activeSocket = null
                    }
                    notifyListeningState()
                }
            }
            L("TCP client loop terminated")
        }
    }

    fun restartServer() {
        serverJob?.cancel()
        serverJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        isServerStarted = false
        connectedClients.forEach { runCatching { it.close() } }
        connectedClients.clear()
        isConnected = false
        notifyListeningState()
        startServer()
    }

    fun restartClient() {
        clientJob?.cancel()
        clientJob = null
        runCatching { activeSocket?.close() }
        activeSocket = null
        isConnected = false
        notifyListeningState()
        startClient()
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
        serverPort = null
        clientTarget = null

        notifyListeningState()
        L("TCP controller stopped")
    }

    private fun manageConnection(socket: Socket, role: String) {
        val input = socket.getInputStream()
        L("TCP $role: connection active")
        try {
            // Welcome message to the peer (mirrors RFCOMM) — lets you verify the protocol
            runCatching { socket.getOutputStream().apply { write(WELCOME_MESSAGE.toByteArray()); flush() } }
            val buffer = ByteArray(1024)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                val received = String(buffer, 0, bytes)
                L("TCP $role received: $received")
                showReceivedMessage(received)
            }
        } catch (e: IOException) {
            LE("TCP $role: connection error", e)
        } finally {
            runCatching { socket.close() }
            L("TCP $role: connection terminated")
        }
    }

    fun sendProcessedData(processedString: String) {
        val data = processedString.toByteArray(Charsets.UTF_8)

        // Server mode: broadcast to all connected clients. A server can't force an absent
        // peer to reconnect (the peer connects to us), so there's nothing to retry against.
        if (serverJob?.isActive == true) {
            if (connectedClients.isEmpty()) {
                L("TCP server: no clients connected — data dropped")
                return
            }
            connectedClients.forEach { socket ->
                runCatching {
                    val out = socket.getOutputStream()
                    out.write(data)
                    out.flush()
                }.onFailure { e ->
                    LE("TCP broadcast send failed to ${socket.inetAddress.hostAddress ?: socket.inetAddress}", e)
                    // Prune the dead client immediately. Closing the socket also unblocks the
                    // reader coroutine (manageClientConnection), whose finally{} may remove it
                    // again — CopyOnWriteArrayList.remove() is safe to call twice.
                    runCatching { socket.close() }
                    connectedClients.remove(socket)
                    isConnected = connectedClients.isNotEmpty()
                    notifyListeningState()
                }
            }
            L("TCP broadcast to ${connectedClients.size} client(s): $processedString")
            return
        }

        // Client mode: single socket. Mirror RFCOMM — on no connection, trigger a reconnect
        // and retry once so a scan made during a brief drop isn't lost.
        val socket = activeSocket
        if (socket == null || !isConnected) {
            L("TCP client: no active connection — triggering reconnect")
            controllerScope.launch {
                restartClient()
                // Wait for the reconnect to actually establish, up to a bound, instead of a
                // fixed sleep — otherwise "scan isn't lost" only holds on fast networks.
                // Bound by the user's own connect timeout (+ small margin) so a slow
                // network / DNS / connect still gets a fair chance before we give up.
                val connectTimeoutMs = context.getPreference(PreferenceStore.TCP_CLIENT_CONNECT_TIMEOUT_MS)
                    .first().coerceIn(500, 30_000)
                val deadline = System.currentTimeMillis() + connectTimeoutMs + 300
                while (!isConnected && isActive && System.currentTimeMillis() < deadline) {
                    delay(50)
                }
                val retry = activeSocket
                if (retry != null && isConnected) {
                    runCatching {
                        val out = retry.getOutputStream()
                        out.write(data)
                        out.flush()
                        L("TCP sent after reconnect: $processedString")
                    }.onFailure { LE("TCP retry send failed", it) }
                } else {
                    L("TCP reconnect didn't establish in time — data dropped")
                }
            }
            return
        }

        runCatching {
            val out = socket.getOutputStream()
            out.write(data)
            out.flush()
            L("TCP sent: $processedString")
        }.onFailure { e ->
            LE("TCP send failed", e)
            controllerScope.launch { restartClient() }
        }
    }
}
