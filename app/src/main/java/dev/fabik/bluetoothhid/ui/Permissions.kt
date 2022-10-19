package dev.fabik.bluetoothhid.ui

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalButton
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
    val permissions = mutableListOf(
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
    }

    val bluetoothPermission = rememberMultiplePermissionsState(permissions)

    if (bluetoothPermission.allPermissionsGranted) {
        content()
    } else {
        if (bluetoothPermission.shouldShowRationale) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.bluetooth_permission))

                Spacer(Modifier.height(16.dp))

                FilledTonalButton(onClick = {
                    bluetoothPermission.launchMultiplePermissionRequest()
                }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.request_again))
                }
            }
        } else {
            SideEffect {
                bluetoothPermission.launchMultiplePermissionRequest()
            }
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
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.camera_permission))

            Spacer(Modifier.height(16.dp))

            FilledTonalButton(onClick = {
                cameraPermission.launchPermissionRequest()
            }, Modifier.align(Alignment.CenterHorizontally)) {
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