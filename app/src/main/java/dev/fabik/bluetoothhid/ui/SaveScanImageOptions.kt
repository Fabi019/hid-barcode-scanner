package dev.fabik.bluetoothhid.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveScanImageOptionsModal() {
    var saveScanEnabled by rememberPreferenceNull(PreferenceStore.SAVE_SCAN)

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    ButtonPreference(
        title = "Save scan image",
        desc = "Stores the scan to the gallery",
        icon = Icons.Default.Photo,
        onClick = { showSheet = true },
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(
                    Modifier
                        .height(32.dp)
                        .padding(horizontal = 24.dp)
                )
                saveScanEnabled?.let { c ->
                    Switch(c, onCheckedChange = {
                        saveScanEnabled = it
                    }, modifier = Modifier.semantics(mergeDescendants = true) {
                        stateDescription = "Save scan image is ${if (c) "On" else "Off"}"
                    })
                }
            }
        }
    )

    if (showSheet) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = { showSheet = false },
            content = {
                SaveToImageOptionsContent()
            }
        )
    }
}

@Composable
@Preview(showBackground = true)
fun SaveToImageOptionsContent() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Save scan image",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(4.dp))

        FolderPicker()

        Spacer(Modifier.height(4.dp))

        AdvancedToggleOption("Crop image", PreferenceStore.SAVE_SCAN_CROP)

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun FolderPicker() {
    val context = LocalContext.current

    var scanPath by rememberPreference(PreferenceStore.SAVE_SCAN_PATH)
    val currentUri by remember { derivedStateOf { if (scanPath.isNotBlank()) scanPath.toUri() else null } }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            // Persist permission (so you keep access after app restart)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            currentUri?.let { prevUri ->
                contentResolver.releasePersistableUriPermission(prevUri, takeFlags)
            }

            // Persist the permission
            contentResolver.takePersistableUriPermission(it, takeFlags)
            scanPath = it.toString()
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            currentUri?.toString() ?: "No path selected",
            onValueChange = {},
            readOnly = true,
            label = { Text("Output folder") },
            trailingIcon = {
                IconButton(onClick = {
                    folderPickerLauncher.launch(currentUri)
                }) {
                    Icon(Icons.Default.Folder, null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
