package dev.fabik.bluetoothhid.ui

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.SystemBroadcastReceiver

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequiresBluetoothPermission(
    content: @Composable () -> Unit
) {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissions.add(android.Manifest.permission.BLUETOOTH)
        permissions.add(android.Manifest.permission.BLUETOOTH_ADMIN)
    }

    val bluetoothPermission = rememberMultiplePermissionsState(permissions)

    if (bluetoothPermission.allPermissionsGranted) {
        content()
    } else {
        Column(
            Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Text(stringResource(R.string.bluetooth_permission))

            FilledTonalButton(onClick = {
                bluetoothPermission.launchMultiplePermissionRequest()
            }) {
                Text(stringResource(R.string.request_again))
            }
        }

        SideEffect {
            bluetoothPermission.launchMultiplePermissionRequest()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequiresCameraPermission(
    content: @Composable () -> Unit
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        content()
    } else {
        Column(
            Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.NoPhotography,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Text(stringResource(R.string.camera_permission))

            FilledTonalButton(onClick = {
                cameraPermission.launchPermissionRequest()
            }) {
                Text(stringResource(R.string.request_permission))
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequireLocationPermission(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // On android 12+ we don't need location permission to scan for bluetooth devices
        content()
    } else {
        val locationPermission = rememberMultiplePermissionsState(
            listOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )

        if (!locationPermission.allPermissionsGranted) {
            Column {
                Text(stringResource(R.string.location_permission), style = Typography.labelMedium)

                FilledTonalButton(onClick = {
                    locationPermission.launchMultiplePermissionRequest()
                }) {
                    Text(stringResource(R.string.request_permission))
                }
            }
        } else {
            val locationManager = remember {
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            }

            var enabledState by remember { mutableStateOf(locationManager.isLocationEnabled) }

            SystemBroadcastReceiver(LocationManager.MODE_CHANGED_ACTION) {
                it?.let {
                    enabledState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.getBooleanExtra(LocationManager.EXTRA_LOCATION_ENABLED, false)
                    } else {
                        locationManager.isLocationEnabled
                    }
                }
            }

            if (!enabledState) {
                Column {
                    Text(stringResource(R.string.location_enable), style = Typography.labelMedium)

                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) {
                        Text(stringResource(R.string.open_location_settings))
                    }
                }
            } else {
                content()
            }
        }
    }
}
