package dev.fabik.bluetoothhid.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController

@Composable
fun Dropdown(
    navHostController: NavHostController,
    activity: Activity = LocalContext.current as Activity
) {
    var showMenu by remember {
        mutableStateOf(false)
    }

    IconButton(onClick = { showMenu = !showMenu }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More"
        )
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = {
                navHostController.navigate(Routes.Settings)
                showMenu = false
            })
        DropdownMenuItem(
            text = { Text("Exit") },
            onClick = { activity.finishAfterTransition() })
    }
}