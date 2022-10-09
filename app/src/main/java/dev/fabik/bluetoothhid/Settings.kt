package dev.fabik.bluetoothhid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.settings)) },
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
                    Text(
                        stringResource(R.string.connection),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.auto_connect),
                        desc = stringResource(R.string.auto_connect_desc),
                        icon = Icons.Default.Link,
                        preference = PrefKeys.AUTO_CONNECT
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.show_unnamed),
                        desc = stringResource(R.string.show_unnamed_desc),
                        icon = Icons.Default.DeviceUnknown,
                        preference = PrefKeys.SHOW_UNNAMED
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.appearance),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    ComboBoxPreference(
                        title = stringResource(R.string.theme),
                        desc = stringResource(R.string.theme_desc),
                        icon = Icons.Default.AutoFixHigh,
                        values = stringArrayResource(R.array.theme_values),
                        preference = PrefKeys.THEME
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.dynamic_theme),
                        desc = stringResource(R.string.dynamic_theme_desc),
                        icon = Icons.Default.AutoAwesome,
                        preference = PrefKeys.DYNAMIC_THEME
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scanner),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.front_camera),
                        desc = stringResource(R.string.front_camera_desc),
                        icon = Icons.Default.FlipCameraAndroid,
                        preference = PrefKeys.FRONT_CAMERA
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.flash_toggle),
                        desc = stringResource(R.string.flash_toggle_desc),
                        icon = Icons.Default.FlashOn,
                        preference = PrefKeys.SHOW_FLASH
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.restrict_area),
                        desc = stringResource(R.string.restrict_area_desc),
                        icon = Icons.Default.CropFree,
                        preference = PrefKeys.RESTRICT_AREA
                    )
                }

                item {
                    ComboBoxPreference(
                        title = stringResource(R.string.overlay_type),
                        desc = stringResource(R.string.overlay_type_desc),
                        icon = Icons.Default.CenterFocusWeak,
                        values = stringArrayResource(R.array.overlay_values),
                        preference = PrefKeys.OVERLAY_TYPE
                    )
                }

                item {
                    ComboBoxPreference(
                        title = stringResource(R.string.scan_freq),
                        desc = stringResource(R.string.scan_freq_desc),
                        icon = Icons.Default.ShutterSpeed,
                        values = stringArrayResource(R.array.scan_freq_values),
                        preference = PrefKeys.SCAN_FREQUENCY
                    )
                }

                item {
                    ComboBoxPreference(
                        title = stringResource(R.string.scan_res),
                        desc = stringResource(R.string.scan_res_desc),
                        icon = Icons.Default.Hd,
                        values = stringArrayResource(R.array.scan_res_values),
                        preference = PrefKeys.SCAN_RESOLUTION
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.auto_send),
                        desc = stringResource(R.string.auto_send_desc),
                        icon = Icons.Default.Send,
                        preference = PrefKeys.AUTO_SEND
                    )
                }

                item {
                    ComboBoxPreference(
                        title = stringResource(R.string.extra_keys),
                        desc = stringResource(R.string.extra_keys_desc),
                        icon = Icons.Default.AddCircle,
                        values = stringArrayResource(R.array.extra_keys_values),
                        preference = PrefKeys.EXTRA_KEYS
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.play_sound),
                        desc = stringResource(R.string.play_sound_desc),
                        icon = Icons.Default.VolumeUp,
                        preference = PrefKeys.PLAY_SOUND
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.raw_value),
                        desc = stringResource(R.string.raw_value_desc),
                        icon = Icons.Default.Description,
                        preference = PrefKeys.RAW_VALUE
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.about), color = MaterialTheme.colorScheme.primary)
                }

                item {
                    val uriHandler = LocalUriHandler.current

                    ButtonPreference(
                        title = stringResource(R.string.repository),
                        desc = "https://github.com/Fabi019/hid-barcode-scanner",
                        icon = Icons.Default.Code
                    ) {
                        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner")
                    }
                }

                item {
                    ButtonPreference(
                        title = stringResource(R.string.build_version),
                        desc = "${BuildConfig.BUILD_TYPE} v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        icon = Icons.Default.Info
                    )

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
