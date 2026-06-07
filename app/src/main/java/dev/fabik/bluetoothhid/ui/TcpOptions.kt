package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceStateBlocking
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpServerOptionsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.tcp_server_settings)) },
        leadingIcon = { Icon(Icons.Default.Wifi, null) },
        onClick = { showSheet = true }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = { TcpServerOptionsContent() }
        )
    }
}

@Composable
fun TcpServerOptionsContent() {
    val context = LocalContext.current
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.tcp_server_settings),
            style = MaterialTheme.typography.titleLarge
        )

        // Port
        val portPref by context.getPreferenceStateBlocking(PreferenceStore.TCP_SERVER_PORT)
        val portState = rememberTextFieldState(portPref)
        LaunchedEffect(portPref) {
            if (portState.text != portPref) portState.setTextAndPlaceCursorAtEnd(portPref)
        }
        DisposableEffect(Unit) {
            onDispose {
                val p = portState.text.toString().toIntOrNull()?.coerceIn(1, 65535)
                if (p != null) runBlocking { context.setPreference(PreferenceStore.TCP_SERVER_PORT, p.toString()) }
            }
        }
        OutlinedTextField(
            state = portState,
            label = { Text(stringResource(R.string.tcp_server_port)) },
            inputTransformation = InputTransformation.byValue { _, proposed ->
                if (proposed.isEmpty() || proposed.toString().toIntOrNull() != null) proposed else portState.text
            },
            keyboardOptions = numberKeyboard,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(2.dp)
        )

        // Max clients
        val maxPref by context.getPreferenceStateBlocking(PreferenceStore.TCP_SERVER_MAX_CLIENTS)
        val maxState = rememberTextFieldState(maxPref.toString())
        LaunchedEffect(maxPref) {
            if (maxState.text.toString().toIntOrNull() != maxPref) maxState.setTextAndPlaceCursorAtEnd(maxPref.toString())
        }
        DisposableEffect(Unit) {
            onDispose {
                val v = maxState.text.toString().toIntOrNull()?.coerceIn(1, 10)
                if (v != null) runBlocking { context.setPreference(PreferenceStore.TCP_SERVER_MAX_CLIENTS, v) }
            }
        }
        OutlinedTextField(
            state = maxState,
            label = { Text(stringResource(R.string.tcp_server_max_clients)) },
            inputTransformation = InputTransformation.byValue { _, proposed ->
                if (proposed.isEmpty() || proposed.toString().toIntOrNull() != null) proposed else maxState.text
            },
            keyboardOptions = numberKeyboard,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(2.dp)
        )

        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TcpClientOptionsModal() {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.tcp_client_settings)) },
        leadingIcon = { Icon(Icons.Default.Wifi, null) },
        onClick = { showSheet = true }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = { TcpClientOptionsContent() }
        )
    }
}

@Composable
fun TcpClientOptionsContent() {
    val context = LocalContext.current
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.tcp_client_settings),
            style = MaterialTheme.typography.titleLarge
        )

        // Host
        val hostPref by context.getPreferenceStateBlocking(PreferenceStore.TCP_CLIENT_HOST)
        val hostState = rememberTextFieldState(hostPref)
        LaunchedEffect(hostPref) {
            if (hostState.text != hostPref) hostState.setTextAndPlaceCursorAtEnd(hostPref)
        }
        DisposableEffect(Unit) {
            onDispose {
                runBlocking { context.setPreference(PreferenceStore.TCP_CLIENT_HOST, hostState.text.toString()) }
            }
        }
        OutlinedTextField(
            state = hostState,
            label = { Text(stringResource(R.string.tcp_client_host)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(2.dp)
        )

        // Port
        val portPref by context.getPreferenceStateBlocking(PreferenceStore.TCP_CLIENT_PORT)
        val portState = rememberTextFieldState(portPref)
        LaunchedEffect(portPref) {
            if (portState.text != portPref) portState.setTextAndPlaceCursorAtEnd(portPref)
        }
        DisposableEffect(Unit) {
            onDispose {
                val p = portState.text.toString().toIntOrNull()?.coerceIn(1, 65535)
                if (p != null) runBlocking { context.setPreference(PreferenceStore.TCP_CLIENT_PORT, p.toString()) }
            }
        }
        OutlinedTextField(
            state = portState,
            label = { Text(stringResource(R.string.tcp_client_port)) },
            inputTransformation = InputTransformation.byValue { _, proposed ->
                if (proposed.isEmpty() || proposed.toString().toIntOrNull() != null) proposed else portState.text
            },
            keyboardOptions = numberKeyboard,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth().padding(2.dp)
        )

        Spacer(Modifier.height(12.dp))
    }
}
