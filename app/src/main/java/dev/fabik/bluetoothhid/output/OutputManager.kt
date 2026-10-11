package dev.fabik.bluetoothhid.output

import android.bluetooth.BluetoothDevice
import dev.fabik.bluetoothhid.utils.ConnectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OutputManager(
    val channels: Map<ConnectionMode, OutputChannel>,
    selection: Flow<OutputSelection>,
    private val scope: CoroutineScope,
) {
    private val lifecycleLock = Mutex()
    private val running = mutableSetOf<ConnectionMode>()
    private val sendGate = Mutex()
    private val _primary = MutableStateFlow<OutputChannel?>(null)
    @Volatile
    private var mirrors: List<OutputChannel> = emptyList()
    private val _isSending = MutableStateFlow(false)
    val isSending = _isSending.asStateFlow()

    private var selectionJob: Job? = null

    val state: StateFlow<ChannelState> = _primary
        .flatMapLatest { it?.state ?: flowOf(ChannelState.Stopped) }
        .stateIn(scope, SharingStarted.Eagerly, ChannelState.Stopped)

    // Device of the primary peer channel: the connected host, or the expected device while
    // waiting (RFCOMM server listening for the selected device). Null in EXTERNAL mode.
    val currentDevice: StateFlow<BluetoothDevice?> = _primary
        .flatMapLatest { channel ->
            channel?.state?.map { s ->
                when (s) {
                    is ChannelState.Connected -> s.device
                    is ChannelState.Waiting -> s.expected
                    else -> null
                }
            } ?: flowOf(null)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Whether the primary channel has a completed send that can be undone.
    val canUndo: StateFlow<Boolean> = _primary
        .flatMapLatest { channel ->
            (channel as? Undoable)?.canUndo ?: flowOf(false)
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val events: Flow<ChannelEvent> = channels.values.map { it.events }.merge()

    init {
        selectionJob = scope.launch {
            selection.distinctUntilChanged().debounce(200).collect(::apply)
        }
    }

    private suspend fun apply(sel: OutputSelection) = lifecycleLock.withLock {
        val mirrorModes =
            sel.mirrors.filter { it != sel.primary && channels.getValue(it).parallelSafe }
        val desired = setOf(sel.primary) + mirrorModes

        (running - desired).forEach { runCatching { channels.getValue(it).stop() } }
        _primary.value = channels.getValue(sel.primary)
        mirrors = mirrorModes.map(channels::getValue)
        (listOf(sel.primary) + mirrorModes)
            .filter { it !in running }
            .forEach { runCatching { channels.getValue(it).start() } }

        running.clear()
        running += desired
    }

    /** Delegates to the primary [PeerChannel]. */
    suspend fun connect(device: BluetoothDevice) {
        (_primary.value as? PeerChannel)?.connect(device)
    }

    /** Delegates to the primary [PeerChannel]; always true when there is no peer to disconnect. */
    suspend fun disconnect(): Boolean =
        (_primary.value as? PeerChannel)?.disconnect() ?: true

    /**
     * Stops every running channel and the selection collector. Without cancelling the collector
     * it would immediately re-apply the selection and restart the channels after teardown.
     */
    suspend fun stop() {
        selectionJob?.cancel()
        selectionJob = null
        lifecycleLock.withLock {
            running.forEach { runCatching { channels.getValue(it).stop() } }
            running.clear()
            mirrors = emptyList()
            _primary.value = null
        }
    }

    /** Debug support (service ACTION_REGISTER): rebuilds the primary channel's transport. */
    suspend fun restartPrimary() = lifecycleLock.withLock {
        val primary = _primary.value ?: return@withLock
        runCatching { primary.stop() }
        runCatching { primary.start() }
    }

    suspend fun send(scan: ScanEvent): SendResult {
        if (!sendGate.tryLock()) return SendResult.Busy
        _isSending.value = true

        try {
            val primary = _primary.value ?: return SendResult.NotConnected
            mirrors.forEach { m -> scope.launch { runCatching { m.send(scan) } } }
            return primary.send(scan)
        } finally {
            _isSending.value = false
            sendGate.unlock()
        }
    }

    fun cancelSend() = (_primary.value as? Cancellable)?.cancelSend()

    suspend fun undo() {
        if (!sendGate.tryLock()) return
        _isSending.value = true

        try {
            val primary = _primary.value as? Undoable ?: return
            return primary.undo()
        } finally {
            _isSending.value = false
            sendGate.unlock()
        }
    }

    inline fun <reified T : OutputChannel> channel(): T? =
        channels.values.filterIsInstance<T>().firstOrNull()
}
