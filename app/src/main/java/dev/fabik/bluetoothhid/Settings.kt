package dev.fabik.bluetoothhid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.ButtonPreference
import dev.fabik.bluetoothhid.ui.CheckBoxPreference
import dev.fabik.bluetoothhid.ui.ComboBoxPreference
import dev.fabik.bluetoothhid.ui.JavaScriptEditorDialog
import dev.fabik.bluetoothhid.ui.SliderPreference
import dev.fabik.bluetoothhid.ui.SwitchPreference
import dev.fabik.bluetoothhid.ui.TextBoxPreference
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull

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
            SectionTitle(R.string.camera)
            CameraSettings()
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
fun ColoredDivider() =
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

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

    ComboBoxPreference(
        title = stringResource(R.string.extra_keys),
        desc = stringResource(R.string.extra_keys_desc),
        icon = Icons.Default.AddCircle,
        values = stringArrayResource(R.array.extra_keys_values),
        preference = PreferenceStore.EXTRA_KEYS
    )

    TextBoxPreference(
        title = stringResource(R.string.custom_template),
        desc = stringResource(R.string.custom_templ_desc),
        descLong = stringResource(R.string.custom_templ_desc_long),
        validator = {
            // TODO: generalize
            it.contains("{CODE}") || it.contains("{CODE_B64}") || it.contains("{CODE_HEX}")
        },
        icon = Icons.Default.LibraryAdd,
        preference = PreferenceStore.TEMPLATE_TEXT
    )

    SwitchPreference(
        title = stringResource(R.string.templates_in_value),
        desc = stringResource(R.string.template_in_value_desc),
        icon = Icons.Default.Expand,
        preference = PreferenceStore.EXPAND_CODE
    )

    SwitchPreference(
        title = stringResource(R.string.auto_send),
        desc = stringResource(R.string.auto_send_desc),
        icon = Icons.AutoMirrored.Filled.Send,
        preference = PreferenceStore.AUTO_SEND
    )

    val jsDialog = rememberDialogState()
    var jsEnabled by rememberPreferenceNull(PreferenceStore.ENABLE_JS)

    ButtonPreference(
        title = stringResource(R.string.custom_javascript),
        desc = stringResource(R.string.custom_js_desc),
        icon = Icons.Default.Code,
        extra = {
            jsEnabled?.let { c ->
                Switch(c, onCheckedChange = {
                    jsEnabled = it
                }, modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = "Custom JavaScript is ${if (c) "On" else "Off"}"
                })
            }
        },
        onClick = jsDialog::open
    )

    JavaScriptEditorDialog(jsDialog)
}

@Composable
fun AppearanceSettings() {
    SwitchPreference(
        title = stringResource(R.string.keep_screen_on),
        desc = stringResource(R.string.keep_screen_on_desc),
        icon = Icons.Default.ScreenLockPortrait,
        preference = PreferenceStore.KEEP_SCREEN_ON
    )

    SwitchPreference(
        title = stringResource(R.string.allow_screen_rotation),
        desc = stringResource(R.string.allow_screen_rotation_desc),
        icon = Icons.Default.ScreenRotation,
        preference = PreferenceStore.ALLOW_SCREEN_ROTATION
    )

    SwitchPreference(
        title = stringResource(R.string.full_screen_scanner),
        desc = stringResource(R.string.full_screen_scanner_desc),
        icon = Icons.Default.Fullscreen,
        preference = PreferenceStore.SCANNER_FULL_SCREEN
    )

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
fun CameraSettings() {
    SwitchPreference(
        title = stringResource(R.string.front_camera),
        desc = stringResource(R.string.front_camera_desc),
        icon = Icons.Default.FlipCameraAndroid,
        preference = PreferenceStore.FRONT_CAMERA
    )

    ComboBoxPreference(
        title = stringResource(R.string.focus_mode),
        desc = stringResource(R.string.focus_mode_desc),
        values = stringArrayResource(R.array.focus_mode_values),
        icon = Icons.Default.HdrAuto,
        preference = PreferenceStore.FOCUS_MODE
    )

    SwitchPreference(
        title = stringResource(R.string.fix_exposure),
        desc = stringResource(R.string.fix_exposure_desc),
        icon = Icons.Default.Exposure,
        preference = PreferenceStore.FIX_EXPOSURE
    )

    SwitchPreference(
        title = stringResource(R.string.preview_performance_mode),
        desc = stringResource(R.string.preview_mode_desc),
        icon = Icons.Default.Bolt,
        preference = PreferenceStore.PREVIEW_PERFORMANCE_MODE
    )

    ComboBoxPreference(
        title = stringResource(R.string.scan_res),
        desc = stringResource(R.string.scan_res_desc),
        icon = Icons.Default.Hd,
        values = stringArrayResource(R.array.scan_res_values),
        preference = PreferenceStore.SCAN_RESOLUTION
    )

    SwitchPreference(
        title = stringResource(R.string.auto_zoom),
        desc = stringResource(R.string.zooms_in_on_codes_to_far_away),
        icon = Icons.Default.ZoomIn,
        preference = PreferenceStore.AUTO_ZOOM
    )
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
        title = stringResource(R.string.restrict_area),
        desc = stringResource(R.string.restrict_area_desc),
        icon = Icons.Default.CropFree,
        preference = PreferenceStore.RESTRICT_AREA
    )

    TextBoxPreference(
        title = stringResource(R.string.filter_regex),
        desc = stringResource(R.string.filter_regex_desc),
        descLong = stringResource(R.string.filter_desc_long),
        validator = { it.isBlank() || runCatching { it.toRegex() }.isSuccess },
        icon = Icons.Default.FilterAlt,
        preference = PreferenceStore.SCAN_REGEX
    )

    ComboBoxPreference(
        title = stringResource(R.string.overlay_type),
        desc = stringResource(R.string.overlay_type_desc),
        icon = Icons.Default.CenterFocusWeak,
        values = stringArrayResource(R.array.overlay_values),
        preference = PreferenceStore.OVERLAY_TYPE
    )

    /*ComboBoxPreference(
        title = stringResource(R.string.highlight_type),
        desc = stringResource(R.string.highlight_type_desc),
        icon = Icons.Default.Highlight,
        values = stringArrayResource(R.array.highlight_values),
        preference = PreferenceStore.HIGHLIGHT_TYPE
    )*/

    SwitchPreference(
        title = stringResource(R.string.show_possible),
        desc = stringResource(R.string.highlights_possible_codes),
        icon = Icons.Default.Highlight,
        preference = PreferenceStore.SHOW_POSSIBLE
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

    SwitchPreference(
        title = stringResource(R.string.play_sound),
        desc = stringResource(R.string.play_sound_desc),
        icon = Icons.AutoMirrored.Filled.VolumeUp,
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

    SwitchPreference(
        title = stringResource(R.string.private_mode),
        desc = stringResource(R.string.private_mode_desc),
        icon = Icons.Default.Shield,
        preference = PreferenceStore.PRIVATE_MODE,
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
        runCatching {
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
        }.onFailure {
            // Open page in browser instead
            uriHandler.openUri("https://play.google.com/store/apps/details?id=${context.packageName}")
        }
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
