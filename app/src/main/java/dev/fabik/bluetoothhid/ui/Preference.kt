package dev.fabik.bluetoothhid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonPreference(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    extra: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
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
                Text(desc, style = Typography.labelMedium, softWrap = true)
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