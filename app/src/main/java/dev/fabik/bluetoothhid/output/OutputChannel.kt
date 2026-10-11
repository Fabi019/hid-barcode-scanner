package dev.fabik.bluetoothhid.output

import android.bluetooth.BluetoothDevice
import androidx.annotation.StringRes
import dev.fabik.bluetoothhid.utils.ConnectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface OutputChannel {
    val mode: ConnectionMode
    val state: StateFlow<ChannelState>
    val events: Flow<ChannelEvent>
    val parallelSafe: Boolean get() = false
    suspend fun start()
    suspend fun stop()
    suspend fun send(scan: ScanEvent): SendResult
}

data class OutputSelection(
    val primary: ConnectionMode,
    val mirrors: Set<ConnectionMode>,
)

sealed interface ChannelState {
    data object Stopped : ChannelState
    data object Ready : ChannelState
    data class Connecting(val device: BluetoothDevice) : ChannelState
    data class Waiting(val expected: BluetoothDevice?) : ChannelState
    data class Connected(val device: BluetoothDevice?) : ChannelState
    data class Failed(val cause: Throwable) : ChannelState
}

sealed interface SendResult {
    data class Sent(val typedChars: Int? = null) : SendResult
    data object Busy : SendResult
    data object NotConnected : SendResult
    data object Cancelled : SendResult
    data class Failed(val cause: Throwable) : SendResult
}

interface PeerChannel : OutputChannel {       // HID, RFCOMM
    suspend fun connect(device: BluetoothDevice)
    suspend fun disconnect(): Boolean
}

interface Cancellable {
    fun cancelSend()
}

interface Undoable {
    val canUndo: StateFlow<Boolean>
    suspend fun undo()
}

sealed interface ChannelEvent {
    data class PeerMessage(val text: String) : ChannelEvent          // RFCOMM showReceivedMessage
    data class Delivery(val plugin: String, val ok: Boolean, val detail: String?) : ChannelEvent
    data class Notice(@StringRes val res: Int) : ChannelEvent        // "BT service connected" etc.
}
