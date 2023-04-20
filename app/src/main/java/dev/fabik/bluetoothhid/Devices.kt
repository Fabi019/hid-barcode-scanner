package dev.fabik.bluetoothhid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.fabik.bluetoothhid.bt.removeBond
import dev.fabik.bluetoothhid.ui.*
import dev.fabik.bluetoothhid.ui.model.DevicesViewModel
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.DeviceInfo
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.SystemBroadcastReceiver
import dev.fabik.bluetoothhid.utils.rememberPreferenceDefault

/**
 * Devices screen. Lists all paired devices and also allows to scan for new ones by
 * swiping down from the top.
 * When a device is selected it first tries to connect with it and after
 * a successful connection has been established it navigates to the [Scanner] screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Devices() = with(viewModel<DevicesViewModel>()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val controller = LocalController.current

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices)) },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isScanning) {
                                refresh(controller)
                            } else {
                                controller.cancelScan()
                            }
                        },
                        modifier = Modifier.tooltip(
                            if (isScanning) stringResource(R.string.cancel) else stringResource(R.string.refresh)
                        )
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Close else Icons.Default.Refresh,
                            "Refresh"
                        )
                    }
                    Dropdown()
                },
                scrollBehavior = scrollBehavior
            )
        }) { padding ->
        Box(Modifier.padding(padding)) {
            DeviceContent()
        }
    }
}

/**
 * Content of the [Devices] screen. Handles the swipe refresh and listens for
 * connection changes.
 */
@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("MissingPermission")
@Composable
fun DevicesViewModel.DeviceContent() {
    val dialogState = rememberDialogState()
    val controller = LocalController.current

    DisposableEffect(controller) {
        isScanning = controller.isScanning
        isBluetoothEnabled = controller.bluetoothEnabled

        if (pairedDevices.isEmpty()) {
            pairedDevices.addAll(controller.pairedDevices)
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
        dialogState,
        stringResource(R.string.connecting),
        stringResource(R.string.connect_help)
    )

    BroadcastListener()

    val pullRefreshState =
        rememberPullRefreshState(isRefreshing, { refresh(controller) })

    Box(Modifier.pullRefresh(pullRefreshState)) {
        DeviceList(controller::connect)

        PullRefreshIndicator(
            isRefreshing,
            pullRefreshState,
            Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Handles the system broadcast events when new devices are found
 * and when a discovery scan started or finished.
 */
@Composable
fun DevicesViewModel.BroadcastListener() {
    SystemBroadcastReceiver(BluetoothAdapter.ACTION_STATE_CHANGED) { intent ->
        isBluetoothEnabled =
            intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON
    }

    SystemBroadcastReceiver(BluetoothAdapter.ACTION_DISCOVERY_STARTED) {
        isScanning = true
        foundDevices.clear()
    }

    SystemBroadcastReceiver(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
        isScanning = false
    }

    SystemBroadcastReceiver(BluetoothDevice.ACTION_FOUND) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            it?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION") it?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }?.let { dev ->
            if (!foundDevices.contains(dev)) {
                foundDevices.add(dev)
            }
        }
    }
}

/**
 * List of devices. Shows the paired devices and the found devices.
 *
 * @param onConnect Callback function when a device is selected.
 */
@SuppressLint("MissingPermission")
@Composable
fun DevicesViewModel.DeviceList(
    onConnect: (BluetoothDevice) -> Unit
) {
    val showUnnamed by rememberPreferenceDefault(PreferenceStore.SHOW_UNNAMED)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(12.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isBluetoothEnabled) {
            item {
                BluetoothDisabledCard()
            }
        } else {
            item {
                Text(
                    stringResource(R.string.scanned_devices),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (isScanning) {
                item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            // Filter out unnamed devices depending on preference
            with(foundDevices.filter { showUnnamed || it.name != null }) {
                if (isEmpty() || !isBluetoothEnabled) {
                    item {
                        RequireLocationPermission {
                            if (!isScanning) {
                                Text(stringResource(R.string.swipe_refresh))
                            }
                        }
                    }
                } else {
                    items(this) { d ->
                        runCatching {
                            DeviceCard(d) {
                                onConnect(d)
                            }
                        }.onFailure {
                            Log.e("DeviceList", "Failed to get device info", it)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.paired_devices),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            if (pairedDevices.isEmpty() || !isBluetoothEnabled) {
                item {
                    Text(stringResource(R.string.no_paired_devices))
                }
            } else {
                items(pairedDevices) {
                    runCatching {
                        DeviceCard(it) {
                            onConnect(it)
                        }
                    }.onFailure {
                        Log.e("DeviceList", "Failed to get device info", it)
                    }
                }
            }
        }
    }
}


/**
 * Card for a device. Shows the name and the address of the device.
 *
 * @param device Bluetooth device to show.
 * @param onClick Callback function when the card is clicked.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    val infoDialog = rememberDialogState()
    val confirmDialog = rememberDialogState()

    val deviceName = device.name ?: ""

    ElevatedCard(
        onClick,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    when (DeviceInfo.deviceClassString(device.bluetoothClass.majorDeviceClass)) {
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
                Text(deviceName)
                Text(
                    device.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = Typography.labelSmall,
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    DeviceDropdown(
                        onConnect = onClick,
                        onInfo = { infoDialog.open() },
                        onRemove = { confirmDialog.open() }
                    ) {
                        Icon(Icons.Default.MoreVert, "More options for $deviceName")
                    }
                } else {
                    Icon(Icons.Default.PlayArrow, "Connect")
                }
            }
        }
    }

    DeviceInfoDialog(infoDialog, device)

    ConfirmDialog(confirmDialog, stringResource(R.string.unpair_device, deviceName), onConfirm = {
        device.removeBond()
        close()
    }) {
        Text(stringResource(R.string.unpair_desc))
    }
}

/**
 * Card that shows if Bluetooth is disabled. Includes a button to trigger a enable dialog.
 */
@SuppressLint("MissingPermission")
@Composable
fun BluetoothDisabledCard() {
    val context = LocalContext.current

    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(64.dp))

            Text(stringResource(R.string.bluetooth_disabled), style = Typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.enable_bluetooth), style = Typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            ) {
                Text(stringResource(R.string.enable_bluetooth_btn))
            }
        }
    }
}

/**
 * Dropdown menu for a device.
 *
 * @param onConnect Callback function when the connect entry is pressed.
 * @param onInfo Callback function when the info entry is pressed.
 * @param onRemove Callback function when the remove entry is pressed.
 * @param icon Icon of the dropdown menu button.
 */
@Composable
fun DeviceDropdown(
    onConnect: () -> Unit = {},
    onInfo: () -> Unit = {},
    onRemove: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showMenu = true },
        modifier = Modifier.tooltip(stringResource(R.string.more)),
        content = icon
    )

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
    ) {
        DropdownMenuItem(
            onClick = {
                showMenu = false
                onConnect()
            },
            text = { Text(stringResource(R.string.connect)) }
        )
        DropdownMenuItem(
            onClick = {
                showMenu = false
                onInfo()
            },
            text = { Text(stringResource(R.string.info)) }
        )
        DropdownMenuItem(
            onClick = {
                showMenu = false
                onRemove()
            },
            text = { Text(stringResource(R.string.unpair)) }
        )
    }
}
