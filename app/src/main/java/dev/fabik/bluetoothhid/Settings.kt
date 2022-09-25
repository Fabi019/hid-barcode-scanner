package dev.fabik.bluetoothhid

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.fabik.bluetoothhid.ui.ButtonPreference
import dev.fabik.bluetoothhid.ui.SwitchPreference
import dev.fabik.bluetoothhid.utils.autoConnect
import dev.fabik.bluetoothhid.utils.dynamicTheme
import dev.fabik.bluetoothhid.utils.setAutoConnect
import dev.fabik.bluetoothhid.utils.setDynamicTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navHostController: NavHostController,
    context: Context = LocalContext.current
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navHostController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val scope = rememberCoroutineScope()

                Text("Connection", color = MaterialTheme.colorScheme.primary)

                val autoConnect by context.autoConnect.collectAsState(null)
                autoConnect?.let {
                    SwitchPreference(
                        title = "Auto Connect",
                        desc = "Connects with the last device on start.",
                        icon = Icons.Default.Link,
                        checked = it
                    ) {
                        scope.launch {
                            context.setAutoConnect(it)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Appearance", color = MaterialTheme.colorScheme.primary)

                val dynamicTheme by context.dynamicTheme.collectAsState(null)
                dynamicTheme?.let {
                    SwitchPreference(
                        title = "Dynamic Theme",
                        desc = "Use the dynamic theme on Android 12+.",
                        icon = Icons.Default.Link,
                        checked = it
                    ) {
                        scope.launch {
                            context.setDynamicTheme(it)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("About", color = MaterialTheme.colorScheme.primary)

                ButtonPreference(
                    title = "BluetoothHID v1.0.0",
                    desc = "https://github.com/Fabi019/AndroidBluetoothHID"
                ) {

                }
            }
        }
    }
}
