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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.fabik.bluetoothhid.utils.localIpAddresses
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
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

        private const val DEFAULT_PORT = 51000
        private val PORT_RANGE = 1..65535
        private const val READ_BUFFER_SIZE = 1024

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

    // Greeting written to the peer on connect (mirrors RFCOMM) — lets you verify the protocol.
    private fun sendWelcome(socket: Socket) {
        runCatching { socket.getOutputStream().apply { write(WELCOME_MESSAGE.toByteArray()); flush() } }
    }

    // Tear down all server-side resources and reset server connection flags. Idempotent.
    private fun closeServerResources() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        isServerStarted = false
        connectedClients.forEach { runCatching { it.close() } }
        connectedClients.clear()
        isConnected = false
    }

    // Tear down the client socket and reset client connection flags. Idempotent.
    private fun closeClientResources() {
        runCatching { activeSocket?.close() }
        activeSocket = null
        isConnected = false
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Serializes outgoing sends. sendProcessedData() now dispatches async (writes must be off
    // the main thread), so without this two rapid scans could interleave bytes on one socket.
    private val sendMutex = Mutex()

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

    // "Listening" drives the status bubble, which is a "waiting for a connection" hint:
    // it shows only while NO connection is active and hides once anything connects.
    //  - Server: accept loop running but no client connected yet. (The loop keeps accepting
    //    more clients regardless — this flag is purely UI state, not the functional state.)
    //  - Client: loop running but not yet connected (connecting).
    fun isListening(): Boolean {
        if (isConnected) return false
        return (serverJob?.isActive == true && isServerStarted) || clientJob?.isActive == true
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
                // Capture into a local — activeSocket is read from multiple threads and could be
                // nulled between the check and use, which would NPE on a !! smart-cast.
                val s = activeSocket
                val target = if (isConnected && s != null)
                    s.run { "${inetAddress?.hostAddress ?: inetAddress}:$port" }
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
        closeClientResources()
        clientTarget = null
        notifyListeningState()

        L("Starting TCP server")
        serverJob = controllerScope.launch {
            val epoch = ++serverEpoch
            var errorCount = 0
            while (isActive) {
                try {
                    val port = context.getPreference(PreferenceStore.TCP_SERVER_PORT).first()
                        .toIntOrNull()?.coerceIn(PORT_RANGE) ?: DEFAULT_PORT
                    val maxClients = context.getPreference(PreferenceStore.TCP_SERVER_MAX_CLIENTS).first()
                        .coerceIn(1, 10)
                    val idleTimeoutMs = context.getPreference(PreferenceStore.TCP_SERVER_CLIENT_IDLE_TIMEOUT_MS).first()
                        .coerceIn(0, 300_000)

                    if (!isServerStarted || serverSocket == null) {
                        // SO_REUSEADDR lets us rebind immediately after a connection closes
                        // without waiting for the OS TIME_WAIT period to expire
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(port))
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
                    closeServerResources()
                    notifyListeningState()
                    val backoff = minOf(250L * errorCount, 5000L) + Random.nextLong(0, 200)
                    delay(backoff)
                }
            }
            // Cleanup on coroutine cancellation — skip if a newer epoch has taken over
            if (epoch == serverEpoch) {
                closeServerResources()
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
            sendWelcome(socket)
            val buffer = ByteArray(READ_BUFFER_SIZE)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                val received = String(buffer, 0, bytes)
                L("TCP server received from $clientAddr: $received")
                showReceivedMessage(received)
            }
        } catch (e: SocketTimeoutException) {
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
        closeServerResources()
        serverPort = null
        notifyListeningState()

        L("Starting TCP client")
        clientJob = controllerScope.launch {
            var errorCount = 0
            while (isActive) {
                var socket: Socket? = null
                var connectionEndedAfterConnect = false
                try {
                    val host = context.getPreference(PreferenceStore.TCP_CLIENT_HOST).first()
                    val port = context.getPreference(PreferenceStore.TCP_CLIENT_PORT).first()
                        .toIntOrNull()?.coerceIn(PORT_RANGE) ?: DEFAULT_PORT
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
                    socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                    L("TCP client connected to $host:$port")

                    isConnected = true
                    errorCount = 0
                    notifyListeningState()

                    withContext(Dispatchers.IO) {
                        manageConnection(socket, "client")
                    }
                    // Reached only when the connection ended after a successful connect —
                    // manageConnection returns on read -1 AND swallows IOException internally,
                    // so this covers any post-connect drop (not just a clean -1).
                    connectionEndedAfterConnect = true

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

                // Backoff after a post-connect disconnect runs AFTER finally has cleared the
                // connection state and notified — otherwise the UI and sendProcessedData() would
                // see a stale "connected" socket for the whole delay. Without it, a server that
                // accepts then immediately closes would force a tight reconnect loop.
                if (connectionEndedAfterConnect && isActive) {
                    val backoff = 1000L + Random.nextLong(0, 200)
                    L("TCP client disconnected — reconnecting in ${backoff}ms")
                    delay(backoff)
                }
            }
            L("TCP client loop terminated")
        }
    }

    fun restartServer() {
        serverJob?.cancel()
        serverJob = null
        closeServerResources()
        notifyListeningState()
        startServer()
    }

    fun restartClient() {
        clientJob?.cancel()
        clientJob = null
        closeClientResources()
        notifyListeningState()
        startClient()
    }

    fun stop() {
        L("Stopping TCP controller")

        serverJob?.cancel()
        serverJob = null
        clientJob?.cancel()
        clientJob = null

        closeClientResources()
        closeServerResources()
        serverPort = null
        clientTarget = null

        notifyListeningState()
        L("TCP controller stopped")
    }

    private fun manageConnection(socket: Socket, role: String) {
        val input = socket.getInputStream()
        L("TCP $role: connection active")
        try {
            sendWelcome(socket)
            val buffer = ByteArray(READ_BUFFER_SIZE)
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
        // All socket I/O MUST run off the main thread. sendString() is invoked from the scan
        // queue on the Main dispatcher, and a synchronous TCP write there throws
        // NetworkOnMainThreadException (StrictMode) — which previously pruned the client and
        // looked like the connection "resetting" on every scan. Dispatch onto the IO scope.
        // (RFCOMM dodges this only because its BluetoothSocket stream isn't network-policed.)
        controllerScope.launch { sendMutex.withLock {
            val data = processedString.toByteArray(Charsets.UTF_8)

            // Server mode: broadcast to all connected clients. A server can't force an absent
            // peer to reconnect (the peer connects to us), so there's nothing to retry against.
            if (serverJob?.isActive == true) {
                // Snapshot recipients so the delivered/attempted count is accurate even as
                // failing sockets get pruned from connectedClients below.
                val recipients = connectedClients.toList()
                if (recipients.isEmpty()) {
                    L("TCP server: no clients connected — data dropped")
                    return@launch
                }
                var delivered = 0
                recipients.forEach { socket ->
                    runCatching {
                        val out = socket.getOutputStream()
                        out.write(data)
                        out.flush()
                    }.onSuccess { delivered++ }.onFailure { e ->
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
                L("TCP broadcast to $delivered/${recipients.size} client(s): $processedString")
                return@launch
            }

            // Client mode: single socket. Mirror RFCOMM — on no connection, trigger a reconnect
            // and retry once so a scan made during a brief drop isn't lost. The retry is bounded
            // (waits for isConnected up to the connect timeout) but not awaited by the send queue.
            val socket = activeSocket
            if (socket == null || !isConnected) {
                L("TCP client: no active connection — triggering reconnect")
                restartClient()
                // Wait for the reconnect to actually establish, up to a bound, instead of a fixed
                // sleep — otherwise "scan isn't lost" only holds on fast networks. Bound by the
                // user's connect timeout (+ margin) so slow network/DNS/connect still gets a chance.
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
                return@launch
            }

            runCatching {
                val out = socket.getOutputStream()
                out.write(data)
                out.flush()
                L("TCP sent: $processedString")
            }.onFailure { e ->
                LE("TCP send failed", e)
                restartClient()
            }
        } }
    }
}
