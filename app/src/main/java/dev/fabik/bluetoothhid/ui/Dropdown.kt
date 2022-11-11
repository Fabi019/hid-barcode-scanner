package dev.fabik.bluetoothhid.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import dev.fabik.bluetoothhid.R

@Composable
fun Dropdown(
    navController: NavController
) {
    val context = LocalContext.current

    var showMenu by remember {
        mutableStateOf(false)
    }

    Box {
        IconButton(
            onClick = { showMenu = !showMenu },
            modifier = Modifier.tooltip(stringResource(R.string.more))
        ) {
            Icon(Icons.Default.MoreVert, "More options")
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                onClick = {
                    showMenu = false
                    navController.navigate(Routes.Settings)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exit)) },
                onClick = {
                    (context as Activity).finishAfterTransition()
                }
            )
        }
    }
}
