package dev.fabik.bluetoothhid.utils

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.InfoDialog
import dev.fabik.bluetoothhid.ui.LoadingDialog
import dev.fabik.bluetoothhid.ui.rememberDialogState
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun RequiresModuleInstallation(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val moduleInstallClient = remember { ModuleInstall.getClient(context) }

    val optionalModuleApi = remember { BarcodeScanning.getClient() }

    val downloadingDialog = rememberDialogState()
    val errorDialog = rememberDialogState()

    val modulePresent by produceState<Boolean?>(null) {
        value = suspendCoroutine<Boolean> { cont ->
            Log.d("ModuleInstaller", "Checking if module is present")
            moduleInstallClient.areModulesAvailable(optionalModuleApi)
                .addOnSuccessListener {
                    if (it.areModulesAvailable()) {
                        Log.d("ModuleInstaller", "Modules are available")
                        cont.resume(true)
                    } else {
                        Log.d("ModuleInstaller", "Modules are not available")
                        val moduleInstallRequest = ModuleInstallRequest.newBuilder()
                            .addApi(optionalModuleApi)
                            .setListener { update ->
                                if (update.installState == STATE_COMPLETED) {
                                    Log.d("ModuleInstaller", "Modules installation completed")
                                    cont.resume(true)
                                } else if (update.installState == STATE_FAILED || update.installState == STATE_CANCELED) {
                                    Log.d("ModuleInstaller", "Modules installation failed")
                                    cont.resume(false)
                                }
                            }
                            .build()
                        moduleInstallClient.installModules(moduleInstallRequest)
                            .addOnSuccessListener { response ->
                                Log.d("ModuleInstaller", "Modules installer started")
                                if (response.areModulesAlreadyInstalled()) {
                                    Log.d("ModuleInstaller", "Modules are already installed")
                                    cont.resume(true)
                                } else {
                                    downloadingDialog.open()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ModuleInstaller", "Failed to start module installer", e)
                                cont.resume(false)
                            }
                    }
                }
                .addOnFailureListener {
                    Log.e("ModuleInstaller", "Failed to check module availability", it)
                    cont.resume(false)
                }
        }
    }

    LoadingDialog(
        dialogState = downloadingDialog,
        title = stringResource(R.string.loading),
        desc = stringResource(R.string.downloading_module),
    )

    InfoDialog(
        dialogState = errorDialog,
        icon = {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = stringResource(R.string.error),
        onDismiss = { errorDialog.close() },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.failed_to_download))
                Text(stringResource(R.string.ensure_internet_and_play))
                Text(stringResource(R.string.download_bundled))
            }
        }
    )

    modulePresent?.let {
        downloadingDialog.close()
        if (it) {
            content()
        } else {
            errorDialog.open()
        }
    }
}
