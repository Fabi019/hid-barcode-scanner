package dev.fabik.bluetoothhid.utils

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.*
import com.google.mlkit.common.sdkinternal.OptionalModuleUtils
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.ui.DialogState
import dev.fabik.bluetoothhid.ui.InfoDialog
import dev.fabik.bluetoothhid.ui.LoadingDialog
import dev.fabik.bluetoothhid.ui.rememberDialogState
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun RequiresModuleInstallation(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val moduleInstallClient = remember { ModuleInstall.getClient(context) }

    val optionalModuleApi =
        remember { OptionalModuleApi { arrayOf(OptionalModuleUtils.FEATURE_BARCODE) } }

    val downloadingDialog = rememberDialogState()
    val errorDialog = rememberDialogState()

    var installState by remember { mutableStateOf<Int?>(null) }
    var errorCode by remember { mutableStateOf<Int?>(null) }

    val modulePresent by produceState<Boolean?>(null) {
        value = suspendCoroutine<Boolean> { cont ->
            installState = null
            errorCode = null

            Log.d("ModuleInstaller", "Checking if module is present")

            moduleInstallClient.areModulesAvailable(optionalModuleApi)
                .addOnSuccessListener {
                    if (it.areModulesAvailable()) {
                        Log.d("ModuleInstaller", "Modules are available")
                        cont.safeResume(true)
                    } else {
                        Log.d("ModuleInstaller", "Modules are not available")
                        val moduleInstallRequest = ModuleInstallRequest.newBuilder()
                            .addApi(optionalModuleApi)
                            .setListener { update ->
                                installState = update.installState

                                Log.d("ModuleInstaller", "Install state: $installState")

                                if (update.installState == STATE_COMPLETED) {
                                    Log.d("ModuleInstaller", "Modules installation completed")
                                    cont.safeResume(true)
                                } else if (update.installState == STATE_FAILED || update.installState == STATE_CANCELED) {
                                    Log.d("ModuleInstaller", "Modules installation failed")
                                    errorCode = update.errorCode
                                    cont.safeResume(false)
                                }
                            }
                            .build()
                        moduleInstallClient.installModules(moduleInstallRequest)
                            .addOnSuccessListener { response ->
                                Log.d("ModuleInstaller", "Modules installer started")

                                if (response.areModulesAlreadyInstalled()) {
                                    Log.d("ModuleInstaller", "Modules are already installed")
                                    cont.safeResume(true)
                                } else {
                                    downloadingDialog.open()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ModuleInstaller", "Failed to start module installer", e)
                                cont.safeResume(false)
                            }
                    }
                }
                .addOnFailureListener {
                    Log.e("ModuleInstaller", "Failed to check module availability", it)
                    cont.safeResume(false)
                }
        }
    }

    ModuleDialogs(
        downloadingDialog,
        errorDialog,
        installState,
        errorCode
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

@Composable
fun ModuleDialogs(
    downloadingDialog: DialogState,
    errorDialog: DialogState,
    installState: Int?,
    errorCode: Int?,
) {
    LoadingDialog(
        dialogState = downloadingDialog,
        title = when (installState) {
            STATE_DOWNLOADING -> stringResource(R.string.downloading)
            STATE_INSTALLING -> stringResource(R.string.installing)
            else -> stringResource(R.string.loading)
        },
        desc = stringResource(R.string.downloading_module),
    )

    InfoDialog(
        dialogState = errorDialog,
        icon = {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = stringResource(R.string.error),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.failed_to_download))
            errorCode?.let { code ->
                Text(
                    stringResource(
                        R.string.error_code,
                        ModuleInstallStatusCodes.getStatusCodeString(code),
                        code
                    )
                )
            }
            Text(stringResource(R.string.ensure_internet_and_play))
            Text(stringResource(R.string.download_bundled))
        }
    }
}

fun <T> Continuation<T>.safeResume(value: T) {
    try {
        resume(value)
    } catch (e: IllegalStateException) {
        Log.w("ModuleInstaller", "safeResume: Multiple calls to resume.", e)
    }
}
