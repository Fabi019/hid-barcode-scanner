package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.fabik.bluetoothhid.utils.PreferenceStore
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
    ListItem(
        modifier = Modifier
            .semantics { stateDescription = desc }
            .clickable(onClick = onClick),
        leadingContent = icon?.let {
            { Icon(icon, contentDescription = null) }
        },
        headlineText = { Text(title) },
        supportingText = { Text(desc) },
        trailingContent = extra
    )
}

@Composable
fun SwitchPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    preference: PreferenceStore.Preference<Boolean>
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
                }, modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = "$title is ${if (c) "On" else "Off"}"
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
    preference: PreferenceStore.Preference<Int>
) {
    var selectedItem by rememberPreferenceNull(preference)

    ComboBoxPreference(
        title,
        desc,
        selectedItem,
        values,
        icon,
        onReset = { selectedItem = preference.defaultValue }) {
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
    onReset: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val dialogState = rememberDialogState()

    selectedItem?.let { s ->
        ComboBoxDialog(dialogState, title, s, values, onReset = onReset, description = desc) {
            onSelect(it)
        }
    }

    ButtonPreference(title, values[selectedItem ?: 0], icon) {
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
    preference: PreferenceStore.Preference<Float>
) {
    var value by rememberPreferenceNull(preference)

    SliderPreference(
        title,
        desc,
        valueFormat,
        value,
        steps,
        range,
        icon,
        onReset = { value = preference.defaultValue }
    ) {
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
    onReset: () -> Unit,
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
            onReset = onReset,
            description = desc
        ) {
            onSelect(it)
        }
    }

    ButtonPreference(title, valueFormat.format(value), icon) {
        dialogState.open()
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    desc: String,
    descLong: String? = desc,
    valueStrings: Array<String>,
    icon: ImageVector? = null,
    preference: PreferenceStore.Preference<Set<String>>
) {
    var value by rememberPreferenceNull(preference)

    CheckBoxPreference(
        title,
        desc,
        descLong,
        selectedValues = value?.map { v -> v.toInt() }?.toSet(),
        valueStrings,
        icon,
        onReset = { value = preference.defaultValue }
    ) {
        value = it.map { v -> v.toString() }.toSet()
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    desc: String,
    descLong: String? = desc,
    selectedValues: Set<Int>?,
    valueStrings: Array<String>,
    icon: ImageVector? = null,
    onReset: () -> Unit,
    onSelect: (Set<Int>) -> Unit
) {
    val dialogState = rememberDialogState()

    selectedValues?.let {
        CheckBoxDialog(
            dialogState,
            title,
            it,
            valueStrings,
            onReset = onReset,
            description = descLong
        ) { v ->
            onSelect(v.toSet())
        }
    }

    ButtonPreference(title, desc, icon) {
        dialogState.open()
    }
}
