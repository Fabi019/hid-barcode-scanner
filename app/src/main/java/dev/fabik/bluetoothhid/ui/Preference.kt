package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.*
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
        shape = MaterialTheme.shapes.small,
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
    values: Array<String>,
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
    values: Array<String>,
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

@Composable
fun SliderPreference(
    title: String,
    desc: String,
    valueFormat: String = "%f",
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    icon: ImageVector? = null,
    preference: PrefKeys.Pref<Float>
) {
    var value by rememberPreferenceNull(preference)

    SliderPreference(title, desc, valueFormat, value, steps, range, icon) {
        value = it
    }
}

@Composable
fun SliderPreference(
    title: String,
    desc: String,
    valueFormat: String = "%f",
    value: Float?,
    steps: Int = 0,
    range: ClosedFloatingPointRange<Float>,
    icon: ImageVector? = null,
    onSelect: (Float) -> Unit
) {
    val dialogState = rememberDialogState()

    value?.let {
        SliderDialog(
            dialogState,
            title,
            valueFormat,
            value,
            range,
            steps,
            onDismiss = { close() }) {
            onSelect(it)
            close()
        }
    }

    ButtonPreference(title, desc, icon) {
        dialogState.open()
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    desc: String,
    valueStrings: Array<String>,
    icon: ImageVector? = null,
    preference: PrefKeys.Pref<Set<String>>
) {
    var value by rememberPreferenceNull(preference)

    CheckBoxPreference(
        title,
        desc,
        selectedValues = value?.map { v -> v.toInt() }?.toSet(),
        valueStrings,
        icon
    ) {
        value = it.map { v -> v.toString() }.toSet()
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    desc: String,
    selectedValues: Set<Int>?,
    valueStrings: Array<String>,
    icon: ImageVector? = null,
    onSelect: (Set<Int>) -> Unit
) {
    val dialogState = rememberDialogState()

    selectedValues?.let {
        CheckBoxDialog(dialogState, title, it, valueStrings, onDismiss = { close() }) { v ->
            onSelect(v.toSet())
            close()
        }
    }

    ButtonPreference(title, desc, icon) {
        dialogState.open()
    }
}