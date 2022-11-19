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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.fabik.bluetoothhid.bt.BluetoothController
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
 *
 * @param navController Navigation controller to navigate to the [Scanner] and [Settings] screens.
 * @param controller Bluetooth controller to get devices and connect to them.
 */
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
        Box(Modifier.padding(padding)) {
            DeviceContent(controller) {
                navController.navigate(Routes.Main)
            }
        }
    }
}

/**
 * Content of the [Devices] screen. Handles the swipe refresh and listens for
 * connection changes.
 *
 * @param controller Bluetooth controller to get devices and connect to them.
 * @param viewModel View model to store the view state.
 * @param onSkip Callback function when the user presses the skip button.
 */
@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("MissingPermission")
@Composable
fun DeviceContent(
    controller: BluetoothController,
    viewModel: DevicesViewModel = viewModel(),
    onSkip: () -> Unit
) = with(viewModel) {
    val dialogState = rememberDialogState()

    DisposableEffect(controller) {
        isScanning = controller.isScanning

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
        DeviceList(
            onConnect = controller::connect,
            onSkip = onSkip
        )

        PullRefreshIndicator(isRefreshing, pullRefreshState, Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Handles the system broadcast events when new devices are found
 * and when a discovery scan started or finished.
 */
@Composable
fun DevicesViewModel.BroadcastListener() {
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
}

/**
 * List of devices. Shows the paired devices and the found devices.
 *
 * @param onConnect Callback function when a device is selected.
 * @param onSkip Callback function when the user presses the skip button.
 */
@SuppressLint("MissingPermission")
@Composable
fun DevicesViewModel.DeviceList(
    onConnect: (BluetoothDevice) -> Unit,
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

        // Filter out unnamed devices depending on preference
        with(foundDevices.filter { showUnnamed || it.name != null }) {
            if (isEmpty()) {
                item {
                    RequireLocationPermission {
                        if (!isScanning) {
                            Text(stringResource(R.string.swipe_refresh))
                        }
                    }
                }
            } else {
                items(this) { d ->
                    DeviceCard(d) {
                        onConnect(d)
                    }
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
                DeviceCard(it) {
                    onConnect(it)
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

    val deviceName = device.name ?: stringResource(R.string.unknown)

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

    ConfirmDialog(
        confirmDialog,
        stringResource(R.string.unpair_device, deviceName),
        onConfirm = {
            device.removeBond()
            close()
        }
    ) {
        Text(stringResource(R.string.unpair_desc))
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
