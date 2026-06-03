package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.R
import dev.fabik.bluetoothhid.utils.ProfileManager
import kotlinx.coroutines.launch

@Composable
fun ProfileManageDialog(dialogState: DialogState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profiles by remember { ProfileManager.getProfilesFlow(context) }
        .collectAsStateWithLifecycle(initialValue = setOf(ProfileManager.DEFAULT))
    val activeProfile by ProfileManager.activeProfile.collectAsStateWithLifecycle()

    var showAddField by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val sortedProfiles = remember(profiles) {
        profiles.sortedWith(compareBy({ it != ProfileManager.DEFAULT }, { it }))
    }

    InfoDialog(dialogState, stringResource(R.string.manage_profiles)) {
        Column {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(sortedProfiles) { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (profile != activeProfile) {
                                    scope.launch {
                                        ProfileManager.switchProfile(context, profile)
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        RadioButton(
                            selected = profile == activeProfile,
                            onClick = null
                        )
                        Text(
                            profile,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // Can't delete Default or active profile
                        if (profile != ProfileManager.DEFAULT && profile != activeProfile) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        ProfileManager.deleteProfile(context, profile)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_profile),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(8.dp))

            if (showAddField) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = {
                        newProfileName = it
                        val trimmed = it.trim()
                        nameError = when {
                            trimmed.isEmpty() -> context.getString(R.string.profile_name_empty)
                            profiles.contains(trimmed) -> context.getString(R.string.profile_name_exists)
                            else -> null
                        }
                    },
                    label = { Text(stringResource(R.string.profile_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            enabled = nameError == null && newProfileName.isNotBlank(),
                            onClick = {
                                val name = newProfileName.trim()
                                scope.launch {
                                    ProfileManager.createProfile(context, name)
                                }
                                newProfileName = ""
                                showAddField = false
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                )
            } else {
                TextButton(
                    onClick = { showAddField = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.add_profile))
                }
            }
        }
    }
}
