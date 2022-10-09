package dev.fabik.bluetoothhid.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import dev.fabik.bluetoothhid.R

@Composable
fun Dropdown(
    navHostController: NavHostController
) {
    val activity: Activity = LocalContext.current as Activity

    var showMenu by remember {
        mutableStateOf(false)
    }

    Box {
        IconButton(onClick = { showMenu = !showMenu }) {
            Icon(Icons.Default.MoreVert, "More")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                onClick = {
                    navHostController.navigate(Routes.Settings)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exit)) },
                onClick = { activity.finishAfterTransition() }
            )
        }
    }
}