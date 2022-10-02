package dev.fabik.bluetoothhid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.fabik.bluetoothhid.ui.ButtonPreference
import dev.fabik.bluetoothhid.ui.ComboBoxPreference
import dev.fabik.bluetoothhid.ui.SwitchPreference
import dev.fabik.bluetoothhid.utils.PrefKeys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navHostController: NavHostController
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
            Column(
                Modifier
                    .padding(12.dp, 0.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Connection", color = MaterialTheme.colorScheme.primary)

                SwitchPreference(
                    title = "Auto Connect",
                    desc = "Connects with the last device on start.",
                    icon = Icons.Default.Link,
                    preference = PrefKeys.AUTO_CONNECT
                )

                SwitchPreference(
                    title = "Disable Warnings",
                    desc = "Disables warnings if permissions missing.",
                    icon = Icons.Default.ReportOff,
                    preference = PrefKeys.DISABLE_WARNINGS
                )

                Spacer(Modifier.height(8.dp))
                Text("Appearance", color = MaterialTheme.colorScheme.primary)

                SwitchPreference(
                    title = "Dynamic Theme",
                    desc = "Use the dynamic theme on Android 12+.",
                    icon = Icons.Default.AutoAwesome,
                    preference = PrefKeys.DYNAMIC_THEME
                )

                Spacer(Modifier.height(8.dp))
                Text("Scanner", color = MaterialTheme.colorScheme.primary)

                SwitchPreference(
                    title = "Front Camera",
                    desc = "Use the front camera instead.",
                    icon = Icons.Default.FlipCameraAndroid,
                    preference = PrefKeys.FRONT_CAMERA
                )

                SwitchPreference(
                    title = "Restrict Scan Area",
                    desc = "Displays a rectangle in the screen middle.",
                    icon = Icons.Default.CenterFocusWeak,
                    preference = PrefKeys.RESTRICT_AREA
                )

                ComboBoxPreference(
                    title = "Scan Frequency",
                    desc = "How often the image should be scanned.",
                    icon = Icons.Default.ShutterSpeed,
                    values = listOf("Fastest", "Fast", "Normal", "Slow"),
                    preference = PrefKeys.SCAN_FREQUENCY
                )

                ComboBoxPreference(
                    title = "Scan Resolution",
                    desc = "Resolution of the analysed image.",
                    icon = Icons.Default.Hd,
                    values = listOf("SD", "HD", "FHD"),
                    preference = PrefKeys.SCAN_RESOLUTION
                )

                SwitchPreference(
                    title = "Auto Send",
                    desc = "Automatically send any detected codes.",
                    preference = PrefKeys.AUTO_SEND
                )

                ComboBoxPreference(
                    title = "Extra keys",
                    desc = "Specify which key should be appended.",
                    values = listOf("None", "Enter", "Tab"),
                    preference = PrefKeys.EXTRA_KEYS
                )

                SwitchPreference(
                    title = "Play Sound",
                    desc = "Plays a sound when scanning a new code.",
                    icon = Icons.Default.VolumeUp,
                    preference = PrefKeys.PLAY_SOUND
                )

                SwitchPreference(
                    title = "Raw Value",
                    desc = "Extracts the raw data value from the code.",
                    preference = PrefKeys.RAW_VALUE
                )

                Spacer(Modifier.height(8.dp))
                Text("About", color = MaterialTheme.colorScheme.primary)

                val uriHandler = LocalUriHandler.current

                ButtonPreference(
                    title = "Repository",
                    desc = "https://github.com/Fabi019/hid-barcode-scanner"
                ) {
                    uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner")
                }

                ButtonPreference(
                    title = "Build-Version",
                    desc = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }
}
