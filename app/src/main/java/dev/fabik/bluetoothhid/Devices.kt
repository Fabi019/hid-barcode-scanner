package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.Dropdown
import dev.fabik.bluetoothhid.ui.LoadingDialog
import dev.fabik.bluetoothhid.ui.RequireLocationPermission
import dev.fabik.bluetoothhid.ui.Routes
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.SystemBroadcastReceiver
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Devices(
    navHostController: NavHostController,
    bluetoothController: BluetoothController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                actions = {
                    Dropdown(navHostController)
                }
            )
        }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            DeviceList(navHostController, bluetoothController)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceList(
    navHostController: NavHostController,
    bluetoothController: BluetoothController
) {
    val foundDevices = remember {
        mutableStateListOf<BluetoothDevice>()
    }

    var isScanning by remember {
        mutableStateOf(false)
    }

    val dialogState = rememberDialogState()

    DisposableEffect(bluetoothController) {
        val listener = bluetoothController.registerListener { _, state ->
            dialogState.openState = when (state) {
                BluetoothProfile.STATE_CONNECTING -> true
                BluetoothProfile.STATE_DISCONNECTING -> true
                else -> false // Only close if connected or fully disconnected
            }
        }

        onDispose {
            bluetoothController.unregisterListener(listener)
        }
    }

    LoadingDialog(
        dialogState, stringResource(R.string.connecting), stringResource(R.string.please_wait)
    )

    SystemBroadcastReceiver(BluetoothAdapter.ACTION_DISCOVERY_STARTED) {
        Log.d("Discovery", "isDiscovering")
        isScanning = true
        foundDevices.clear()
    }

    SystemBroadcastReceiver(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
        Log.d("Discovery", "FinishedDiscovering")
        isScanning = false
    }

    SystemBroadcastReceiver(BluetoothDevice.ACTION_FOUND) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it?.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION") it?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }?.let { dev ->
            if (!foundDevices.contains(dev)) {
                foundDevices.add(dev)
            }

            Log.d("Discovery", "Found: $dev")
        }
    }

    var isRefreshing by remember {
        mutableStateOf(false)
    }

    var devices by remember {
        mutableStateOf(bluetoothController.pairedDevices())
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            devices = bluetoothController.pairedDevices()
            bluetoothController.scanDevices()
            delay(500)
            isRefreshing = false
        }
    }

    val showUnnamed by rememberPreferenceDefault(PrefKeys.SHOW_UNNAMED)

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(state = swipeRefreshState, onRefresh = {
        if (!isScanning) {
            isRefreshing = true
        }
    }, indicator = { state, trigger ->
        SwipeRefreshIndicator(
            state = state,
            refreshTriggerDistance = trigger,
            scale = true,
            backgroundColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
        )
    }) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(12.dp, 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.scanned_devices),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isScanning) {
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            if (foundDevices.isEmpty()) {
                item {
                    RequireLocationPermission {
                        if (!isScanning) {
                            Text(stringResource(R.string.swipe_refresh))
                        }
                    }
                }
            } else {
                items(foundDevices) { d ->
                    if (d.name == null && !showUnnamed)
                        return@items
                    Device(d.name ?: stringResource(R.string.unknown), d.address) {
                        bluetoothController.connect(d)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.paired_devices),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (devices.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_paired_devices))
                }
            } else {
                items(devices.toList()) {
                    Device(it.name, it.address) {
                        bluetoothController.connect(it)
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { navHostController.navigate(Routes.Main) },
                        Modifier.align(Alignment.Center)
                    ) {
                        Text(stringResource(R.string.skip))
                    }
                }
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Device(name: String, address: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bluetooth, "Connect")
            }
            Column(
                Modifier
                    .padding(4.dp)
                    .weight(1f)
            ) {
                Text(name)
                Text(
                    address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = Typography.labelSmall,
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Connect")
            }
        }
    }
}