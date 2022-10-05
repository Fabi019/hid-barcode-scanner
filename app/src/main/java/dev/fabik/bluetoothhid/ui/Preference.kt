package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    extra: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(56.dp)
    ) {
        Row(
            Modifier
                .padding(4.dp)
                .heightIn(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                icon?.let {
                    Icon(icon, null)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(
                    desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = Typography.labelSmall,
                    softWrap = true
                )
            }
            extra?.let {
                extra()
            }
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    preference: PrefKeys.Pref<Boolean>
) {
    var checked by rememberPreferenceNull(preference)

    SwitchPreference(title, desc, icon, checked) {
        checked = it
    }
}

@Composable
fun SwitchPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    checked: Boolean?,
    onToggle: (Boolean) -> Unit
) {
    ButtonPreference(
        title, desc, icon, {
            checked?.let { c ->
                Switch(c, onCheckedChange = {
                    onToggle(it)
                })
            }
        }
    ) {
        checked?.let {
            onToggle(!it)
        }
    }
}

@Composable
fun ComboBoxPreference(
    title: String,
    desc: String,
    values: List<String>,
    icon: ImageVector? = null,
    preference: PrefKeys.Pref<Int>
) {
    var selectedItem by rememberPreferenceNull(preference)

    ComboBoxPreference(title, desc, selectedItem, values, icon) {
        selectedItem = it
    }
}

@Composable
fun ComboBoxPreference(
    title: String,
    desc: String,
    selectedItem: Int?,
    values: List<String>,
    icon: ImageVector? = null,
    onSelect: (Int) -> Unit
) {
    val dialogState = rememberDialogState()

    selectedItem?.let { s ->
        ComboBoxDialog(dialogState, title, s, values, onDismiss = { close() }) {
            onSelect(it)
            close()
        }
    }

    ButtonPreference(title, desc, icon) {
        dialogState.open()
    }
}