package dev.fabik.bluetoothhid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.*
import dev.fabik.bluetoothhid.utils.PreferenceStore

@Composable
fun SettingsContent() {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(0.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle(R.string.connection)
            ConnectionSettings()
            ColoredDivider()
        }

        item {
            SectionTitle(R.string.appearance)
            AppearanceSettings()
            ColoredDivider()
        }

        item {
            SectionTitle(R.string.scanner)
            ScannerSettings()
            ColoredDivider()
        }

        item {
            SectionTitle(R.string.about)
            AboutSettings()
        }
    }
}

@Composable
fun ColoredDivider() = Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

@Composable
fun SectionTitle(@StringRes id: Int) {
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(id),
        Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun ConnectionSettings() {
    SwitchPreference(
        title = stringResource(R.string.auto_connect),
        desc = stringResource(R.string.auto_connect_desc),
        icon = Icons.Default.Link,
        preference = PreferenceStore.AUTO_CONNECT
    )

    SwitchPreference(
        title = stringResource(R.string.show_unnamed),
        desc = stringResource(R.string.show_unnamed_desc),
        icon = Icons.Default.DeviceUnknown,
        preference = PreferenceStore.SHOW_UNNAMED
    )

    SliderPreference(
        title = stringResource(R.string.send_delay),
        desc = stringResource(R.string.send_delay_desc),
        valueFormat = stringResource(R.string.send_delay_template),
        range = 0f..100f,
        icon = Icons.Default.Timer,
        preference = PreferenceStore.SEND_DELAY
    )

    ComboBoxPreference(
        title = stringResource(R.string.keyboard_layout),
        desc = stringResource(R.string.keyboard_layout_desc),
        icon = Icons.Default.Keyboard,
        values = stringArrayResource(R.array.keyboard_layout_values),
        preference = PreferenceStore.KEYBOARD_LAYOUT
    )
}

@Composable
fun AppearanceSettings() {
    ComboBoxPreference(
        title = stringResource(R.string.theme),
        desc = stringResource(R.string.theme_desc),
        icon = Icons.Default.AutoFixHigh,
        values = stringArrayResource(R.array.theme_values),
        preference = PreferenceStore.THEME
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchPreference(
            title = stringResource(R.string.dynamic_theme),
            desc = stringResource(R.string.dynamic_theme_desc),
            icon = Icons.Default.AutoAwesome,
            preference = PreferenceStore.DYNAMIC_THEME
        )
    }
}

@Composable
fun ScannerSettings() {
    CheckBoxPreference(
        title = stringResource(R.string.code_types),
        desc = stringResource(R.string.code_types_desc_short),
        descLong = stringResource(R.string.code_types_desc),
        valueStrings = stringArrayResource(R.array.code_types_values),
        icon = Icons.Default.QrCode2,
        preference = PreferenceStore.CODE_TYPES
    )

    SwitchPreference(
        title = stringResource(R.string.front_camera),
        desc = stringResource(R.string.front_camera_desc),
        icon = Icons.Default.FlipCameraAndroid,
        preference = PreferenceStore.FRONT_CAMERA
    )

    SwitchPreference(
        title = stringResource(R.string.restrict_area),
        desc = stringResource(R.string.restrict_area_desc),
        icon = Icons.Default.CropFree,
        preference = PreferenceStore.RESTRICT_AREA
    )

    SwitchPreference(
        title = stringResource(R.string.auto_focus),
        desc = stringResource(R.string.auto_focus_desc),
        icon = Icons.Default.HdrAuto,
        preference = PreferenceStore.AUTO_FOCUS
    )

    ComboBoxPreference(
        title = stringResource(R.string.overlay_type),
        desc = stringResource(R.string.overlay_type_desc),
        icon = Icons.Default.CenterFocusWeak,
        values = stringArrayResource(R.array.overlay_values),
        preference = PreferenceStore.OVERLAY_TYPE
    )

    ComboBoxPreference(
        title = stringResource(R.string.highlight_type),
        desc = stringResource(R.string.highlight_type_desc),
        icon = Icons.Default.Highlight,
        values = stringArrayResource(R.array.highlight_values),
        preference = PreferenceStore.HIGHLIGHT_TYPE
    )

    SwitchPreference(
        title = stringResource(R.string.full_inside),
        desc = stringResource(R.string.full_inside_desc),
        icon = Icons.Default.QrCodeScanner,
        preference = PreferenceStore.FULL_INSIDE
    )

    ComboBoxPreference(
        title = stringResource(R.string.scan_freq),
        desc = stringResource(R.string.scan_freq_desc),
        icon = Icons.Default.ShutterSpeed,
        values = stringArrayResource(R.array.scan_freq_values),
        preference = PreferenceStore.SCAN_FREQUENCY
    )

    ComboBoxPreference(
        title = stringResource(R.string.scan_res),
        desc = stringResource(R.string.scan_res_desc),
        icon = Icons.Default.Hd,
        values = stringArrayResource(R.array.scan_res_values),
        preference = PreferenceStore.SCAN_RESOLUTION
    )

    SwitchPreference(
        title = stringResource(R.string.auto_send),
        desc = stringResource(R.string.auto_send_desc),
        icon = Icons.Default.Send,
        preference = PreferenceStore.AUTO_SEND
    )

    ComboBoxPreference(
        title = stringResource(R.string.extra_keys),
        desc = stringResource(R.string.extra_keys_desc),
        icon = Icons.Default.AddCircle,
        values = stringArrayResource(R.array.extra_keys_values),
        preference = PreferenceStore.EXTRA_KEYS
    )

    SwitchPreference(
        title = stringResource(R.string.play_sound),
        desc = stringResource(R.string.play_sound_desc),
        icon = Icons.Default.VolumeUp,
        preference = PreferenceStore.PLAY_SOUND
    )

    SwitchPreference(
        title = stringResource(R.string.haptic_feedback),
        desc = stringResource(R.string.haptic_feedback_desc),
        icon = Icons.Default.Vibration,
        preference = PreferenceStore.VIBRATE
    )

    SwitchPreference(
        title = stringResource(R.string.raw_value),
        desc = stringResource(R.string.raw_value_desc),
        icon = Icons.Default.Description,
        preference = PreferenceStore.RAW_VALUE
    )
}

@Composable
fun AboutSettings() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    ButtonPreference(
        title = stringResource(R.string.source_code),
        desc = stringResource(R.string.source_code_desc),
        icon = Icons.Default.Code,
    ) {
        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner")
    }

    ButtonPreference(
        title = stringResource(R.string.report_issue),
        desc = stringResource(R.string.report_issue_desc),
        icon = Icons.Default.BugReport,
    ) {
        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner/issues/new")
    }

    ButtonPreference(
        title = stringResource(R.string.rate),
        desc = stringResource(R.string.rate_desc),
        icon = Icons.Default.Star,
    ) {
        uriHandler.openUri("market://details?id=${context.packageName}")
    }

    val shareVia = stringResource(R.string.share_via)

    ButtonPreference(
        title = stringResource(R.string.share),
        desc = stringResource(R.string.share_desc),
        icon = Icons.Default.Share,
    ) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "https://play.google.com/store/apps/details?id=${context.packageName}"
                    )
                    type = "text/plain"
                }, shareVia
            )
        )
    }

    ButtonPreference(
        title = stringResource(R.string.build_version),
        desc = stringResource(
            R.string.build_version_desc,
            BuildConfig.BUILD_TYPE,
            BuildConfig.VERSION_NAME,
            BuildConfig.GIT_COMMIT_HASH,
            BuildConfig.VERSION_CODE,
            BuildConfig.FLAVOR
        ),
        icon = Icons.Default.Info
    ) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}
