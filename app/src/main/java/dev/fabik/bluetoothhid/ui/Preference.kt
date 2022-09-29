package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.theme.Typography
import dev.fabik.bluetoothhid.utils.PrefKeys
import dev.fabik.bluetoothhid.utils.getPreferenceState
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    extra: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(4.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                icon?.let {
                    Icon(imageVector = icon, contentDescription = null)
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
    val context = LocalContext.current

    val checked by context.getPreferenceState(preference)

    checked?.let {
        SwitchPreference(title, desc, icon, checked = it) {
            CoroutineScope(Dispatchers.IO).launch {
                context.setPreference(preference, it)
            }
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ButtonPreference(
        title, desc, icon, {
            Switch(checked, onCheckedChange = {
                onToggle(it)
            })
        }
    ) {
        onToggle(!checked)
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
    val context = LocalContext.current

    val checked by context.getPreferenceState(preference)

    checked?.let {
        ComboBoxPreference(title, desc, selectedItem = it, values, icon) {
            CoroutineScope(Dispatchers.IO).launch {
                context.setPreference(preference, it)
            }
        }
    }
}

@Composable
fun ComboBoxPreference(
    title: String,
    desc: String,
    selectedItem: Int,
    values: List<String>,
    icon: ImageVector? = null,
    onSelect: (Int) -> Unit
) {
    val dialogState = rememberDialogState()

    ComboBoxDialog(dialogState, title, selectedItem, values, onDismiss = { close() }) {
        onSelect(it)
        close()
    }

    ButtonPreference(title, desc, icon) {
        dialogState.open()
    }
}