package dev.fabik.bluetoothhid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp, 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Connection", color = MaterialTheme.colorScheme.primary)
                }

                item {
                    SwitchPreference(
                        title = "Auto Connect",
                        desc = "Connects with the last device on start.",
                        icon = Icons.Default.Link,
                        preference = PrefKeys.AUTO_CONNECT
                    )
                }

                item {
                    SwitchPreference(
                        title = "Show unnamed devices",
                        desc = "Displays bluetooth devices without a name.",
                        icon = Icons.Default.DeviceUnknown,
                        preference = PrefKeys.SHOW_UNNAMED
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Appearance", color = MaterialTheme.colorScheme.primary)
                }

                item {
                    ComboBoxPreference(
                        title = "Theme",
                        desc = "Preferred UI Theme.",
                        icon = Icons.Default.AutoFixHigh,
                        values = listOf("System", "Light", "Dark"),
                        preference = PrefKeys.THEME
                    )
                }

                item {
                    SwitchPreference(
                        title = "Dynamic Theme",
                        desc = "Use the dynamic theme on Android 12+.",
                        icon = Icons.Default.AutoAwesome,
                        preference = PrefKeys.DYNAMIC_THEME
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Scanner", color = MaterialTheme.colorScheme.primary)
                }

                item {
                    SwitchPreference(
                        title = "Front Camera",
                        desc = "Use the front camera instead.",
                        icon = Icons.Default.FlipCameraAndroid,
                        preference = PrefKeys.FRONT_CAMERA
                    )
                }

                item {
                    SwitchPreference(
                        title = "Flash toggle",
                        desc = "Displays a button if the camera supports it.",
                        icon = Icons.Default.FlashOn,
                        preference = PrefKeys.SHOW_FLASH
                    )
                }

                item {
                    SwitchPreference(
                        title = "Restrict Scan Area",
                        desc = "Displays a overlay on the camera screen.",
                        icon = Icons.Default.CropFree,
                        preference = PrefKeys.RESTRICT_AREA
                    )
                }

                item {
                    ComboBoxPreference(
                        title = "Overlay type",
                        desc = "Specify the shape of the overlay.",
                        icon = Icons.Default.CenterFocusWeak,
                        values = listOf("Square (QR-Code)", "Rectangle (Barcode)"),
                        preference = PrefKeys.OVERLAY_TYPE
                    )
                }

                item {
                    ComboBoxPreference(
                        title = "Scan Frequency",
                        desc = "How often the image should be scanned.",
                        icon = Icons.Default.ShutterSpeed,
                        values = listOf("Fastest", "Fast (100ms)", "Normal (500ms)", "Slow (1s)"),
                        preference = PrefKeys.SCAN_FREQUENCY
                    )
                }

                item {
                    ComboBoxPreference(
                        title = "Scan Resolution",
                        desc = "Resolution of the analysed image.",
                        icon = Icons.Default.Hd,
                        values = listOf("SD", "HD", "FHD"),
                        preference = PrefKeys.SCAN_RESOLUTION
                    )
                }

                item {
                    SwitchPreference(
                        title = "Auto Send",
                        desc = "Automatically send any detected codes.",
                        icon = Icons.Default.Send,
                        preference = PrefKeys.AUTO_SEND
                    )
                }

                item {
                    ComboBoxPreference(
                        title = "Extra keys",
                        desc = "Specify which key should be appended.",
                        values = listOf("None", "Return", "Tab"),
                        icon = Icons.Default.AddCircle,
                        preference = PrefKeys.EXTRA_KEYS
                    )
                }

                item {
                    SwitchPreference(
                        title = "Play Sound",
                        desc = "Plays a sound when scanning a new code.",
                        icon = Icons.Default.VolumeUp,
                        preference = PrefKeys.PLAY_SOUND
                    )
                }

                item {
                    SwitchPreference(
                        title = "Raw Value",
                        desc = "Extracts the raw data value from the code.",
                        icon = Icons.Default.Description,
                        preference = PrefKeys.RAW_VALUE
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("About", color = MaterialTheme.colorScheme.primary)
                }

                item {
                    val uriHandler = LocalUriHandler.current

                    ButtonPreference(
                        title = "Repository",
                        desc = "https://github.com/Fabi019/hid-barcode-scanner",
                        icon = Icons.Default.Code
                    ) {
                        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner")
                    }
                }

                item {
                    ButtonPreference(
                        title = "Build-Version",
                        desc = "${BuildConfig.BUILD_TYPE} v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        icon = Icons.Default.Info
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
