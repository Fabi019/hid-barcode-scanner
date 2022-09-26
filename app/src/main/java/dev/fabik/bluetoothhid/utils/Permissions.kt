package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermissions(
    context: Context = LocalContext.current,
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

    val locationPermission = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )

    if (bluetoothPermission.allPermissionsGranted) {
        if (!locationPermission.allPermissionsGranted) {
            if (locationPermission.shouldShowRationale) {
                Toast.makeText(
                    context,
                    "No location permission. Scanning for new devices will NOT work on most devices!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            SideEffect {
                locationPermission.launchMultiplePermissionRequest()
            }
        } else {
            // Check if Location is enabled
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(
                    context,
                    "Location is not enabled. Scanning for new devices will NOT work!",
                    Toast.LENGTH_SHORT
                ).show()

                //startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        }

        content()
    } else {
        if (bluetoothPermission.shouldShowRationale) {
            Column {
                Text("Not all required permissions have been granted.")
                Text("The app can't function correctly without them.")
                Button(onClick = {
                    bluetoothPermission.launchMultiplePermissionRequest()
                }) {
                    Text("Request again")
                }
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
        Button(onClick = {
            cameraPermission.launchPermissionRequest()
        }) {
            Text("Camera Permission")
        }
    }

}