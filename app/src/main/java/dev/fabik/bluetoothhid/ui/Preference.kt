package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberEnumPreference
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull


@Composable
fun ButtonPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    extra: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick),
        supportingContent = { Text(desc) },
        leadingContent = icon?.let {
            { Icon(icon, null) }
        },
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
                Switch(
                    checked = c,
                    onCheckedChange = null
                )
            }
        }
    ) {
        checked?.let {
            onToggle(!it)
        }
    }
}

@Composable
fun <E : Enum<E>> ComboBoxEnumPreference(
    title: String,
    desc: String,
    values: Array<String>,
    icon: ImageVector? = null,
    preference: PreferenceStore.EnumPref<E>,
    enabled: Boolean = true,
    onReset: () -> Unit = {},
) {
    var selectedEnum by rememberEnumPreference(preference)

    ComboBoxPreference(
        title,
        desc,
        selectedEnum.ordinal,
        values,
        icon,
        enabled,
        onReset = { selectedEnum = preference.getDefaultEnum(); onReset() }
    ) {
        selectedEnum = preference.fromOrdinal(it)
    }
}

@Composable
fun ComboBoxPreference(
    title: String,
    desc: String,
    selectedItem: Int?,
    values: Array<String>,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onReset: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val dialogState = rememberDialogState()

    selectedItem?.let { s ->
        ComboBoxDialog(dialogState, title, s, values, onReset = onReset, description = desc) {
            onSelect(it)
        }
    }

    ButtonPreference(title, values[selectedItem ?: 0], icon, enabled = enabled) {
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
    enabled: Boolean = true,
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
        enabled,
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
    enabled: Boolean = true,
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

    ButtonPreference(title, valueFormat.format(value), icon, enabled = enabled) {
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

@Composable
fun TextBoxPreference(
    title: String,
    desc: String,
    descLong: String? = desc,
    validator: (String) -> String? = { null },
    icon: ImageVector? = null,
    enabled: Boolean = true,
    preference: PreferenceStore.Preference<String>
) {
    var value by rememberPreferenceNull(preference)

    TextBoxPreference(
        title,
        desc,
        descLong,
        value,
        validator,
        icon,
        enabled,
        onReset = { value = preference.defaultValue }) {
        value = it
    }
}

@Composable
fun TextBoxPreference(
    title: String,
    desc: String,
    descLong: String? = desc,
    value: String?,
    validator: (String) -> String? = { null },
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onReset: () -> Unit,
    onSelect: (String) -> Unit
) {
    val dialogState = rememberDialogState()

    value?.let {
        TextBoxDialog(
            dialogState,
            title,
            it,
            validator = validator,
            onReset = onReset,
            description = descLong
        ) { v ->
            onSelect(v)
        }
    }

    ButtonPreference(title, if (value.isNullOrEmpty()) desc else value, icon, enabled = enabled) {
        dialogState.open()
    }
}
