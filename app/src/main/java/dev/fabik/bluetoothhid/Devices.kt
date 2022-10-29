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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import dev.fabik.bluetoothhid.bt.BluetoothController
import dev.fabik.bluetoothhid.ui.*
import dev.fabik.bluetoothhid.ui.model.DevicesViewModel
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.DeviceInfo
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.SystemBroadcastReceiver
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Devices(
    navController: NavController,
    controller: BluetoothController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices)) },
                actions = {
                    Dropdown(navController)
                }
            )
        }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            DeviceContent(controller) {
                navController.navigate(Routes.Main)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceContent(
    controller: BluetoothController,
    viewModel: DevicesViewModel = viewModel(),
    onSkip: () -> Unit
) = with(viewModel) {
    val dialogState = rememberDialogState()

    DisposableEffect(controller) {
        if (pairedDevices.isEmpty()) {
            pairedDevices.addAll(controller.pairedDevices())
        }

        val listener = controller.registerListener { _, state ->
            dialogState.openState = when (state) {
                BluetoothProfile.STATE_CONNECTING -> true
                BluetoothProfile.STATE_DISCONNECTING -> true
                else -> false // Only close if connected or fully disconnected
            }
        }

        onDispose {
            controller.unregisterListener(listener)
        }
    }

    LoadingDialog(
        dialogState, stringResource(R.string.connecting), stringResource(R.string.connect_help)
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

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pairedDevices.clear()
            pairedDevices.addAll(controller.pairedDevices())
            controller.scanDevices()
            delay(500)
            isRefreshing = false
        }
    }

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
        DeviceList(
            onClick = { controller.connect(it) },
            onSkip = onSkip
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DevicesViewModel.DeviceList(
    onClick: (BluetoothDevice) -> Unit,
    onSkip: () -> Unit
) {
    val showUnnamed by rememberPreferenceDefault(PreferenceStore.SHOW_UNNAMED)

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
                DeviceCard(
                    d.name ?: stringResource(R.string.unknown),
                    d.address,
                    DeviceInfo.deviceClassString(d.bluetoothClass.majorDeviceClass)
                ) {
                    onClick(d)
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

        if (pairedDevices.isEmpty()) {
            item {
                Text(stringResource(R.string.no_paired_devices))
            }
        } else {
            items(pairedDevices) {
                DeviceCard(
                    it.name,
                    it.address,
                    DeviceInfo.deviceClassString(it.bluetoothClass.majorDeviceClass)
                ) {
                    onClick(it)
                }
            }
        }

        item {
            Box(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onSkip,
                    Modifier.align(Alignment.Center)
                ) {
                    Text(stringResource(R.string.skip))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    name: String,
    address: String,
    type: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    when (type) {
                        "PHONE" -> Icons.Default.Smartphone
                        "AUDIO_VIDEO" -> Icons.Default.Headphones
                        "COMPUTER" -> Icons.Default.Computer
                        "PERIPHERAL" -> Icons.Default.Keyboard
                        else -> Icons.Default.Bluetooth
                    },
                    "Type"
                )
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
