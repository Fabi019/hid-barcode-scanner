package dev.fabik.bluetoothhid.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.rememberPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveScanImageOptionsModal() {
    var saveScanEnabled by rememberPreferenceNull(PreferenceStore.SAVE_SCAN)

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    ButtonPreference(
        title = stringResource(R.string.save_scan_image),
        desc = stringResource(R.string.save_scan_desc),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun SaveToImageOptionsContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.save_scan_image),
            style = MaterialTheme.typography.titleLarge,
        )

        FolderPicker()
        AdvancedEnumSelectionOption(
            stringResource(R.string.crop_mode),
            arrayOf("NONE", "SCAN_AREA", "BARCODE"),
            PreferenceStore.SAVE_SCAN_CROP_MODE
        )
        AdvancedSliderOption(
            stringResource(R.string.image_quality),
            1 to 100,
            PreferenceStore.SAVE_SCAN_QUALITY
        )

        val context = LocalContext.current
        val storedFileName by context.getPreferenceState(PreferenceStore.SAVE_SCAN_FILE_PATTERN)
        val localFileName = rememberTextFieldState()

        storedFileName?.let {
            DisposableEffect(it) {
                localFileName.setTextAndPlaceCursorAtEnd(it)
                onDispose {
                    runBlocking {
                        context.setPreference(
                            PreferenceStore.SAVE_SCAN_FILE_PATTERN,
                            localFileName.text.toString()
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            state = localFileName,
            supportingText = { Text(stringResource(R.string.file_name_pattern_placeholder)) },
            label = { Text(stringResource(R.string.file_name_pattern)) },
            inputTransformation = InputTransformation.byValue { current, proposed ->
                if (proposed.contains("/")) {
                    current
                } else {
                    proposed
                }
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
        )

        Text(
            stringResource(R.string.save_scan_image_hint),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))
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
            label = { Text(stringResource(R.string.output_folder)) },
            trailingIcon = {
                IconButton(onClick = {
                    runCatching {
                        folderPickerLauncher.launch(currentUri)
                    }.onFailure {
                        Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(Icons.Default.Folder, null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp)
        )
    }
}
