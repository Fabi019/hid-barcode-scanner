package dev.fabik.bluetoothhid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.fabik.bluetoothhid.ui.AdvancedOptionsModal
import dev.fabik.bluetoothhid.ui.ButtonPreference
import dev.fabik.bluetoothhid.ui.CheckBoxPreference
import dev.fabik.bluetoothhid.ui.ComboBoxEnumPreference
import dev.fabik.bluetoothhid.ui.CustomKeysDialog
import dev.fabik.bluetoothhid.ui.JavaScriptEditorDialog
import dev.fabik.bluetoothhid.ui.SaveScanImageOptionsModal
import dev.fabik.bluetoothhid.ui.SliderPreference
import dev.fabik.bluetoothhid.ui.SwitchPreference
import dev.fabik.bluetoothhid.ui.TextBoxPreference
import dev.fabik.bluetoothhid.ui.rememberDialogState
import dev.fabik.bluetoothhid.utils.PreferenceStore
import dev.fabik.bluetoothhid.utils.rememberPreferenceNull
import dev.fabik.bluetoothhid.utils.setPreference
import kotlinx.coroutines.launch

@Composable
fun SettingsContent() {
    val context = LocalContext.current
    val strings = remember {
        SettingsStrings(context)
    }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp, 0.dp)
    ) {
        item(contentType = "section") {
            SectionTitle(strings[R.string.connection])
            ConnectionSettings(strings)
            ColoredDivider()
        }

        item(contentType = "section") {
            SectionTitle(strings[R.string.appearance])
            AppearanceSettings(strings)
            ColoredDivider()
        }

        item(contentType = "section") {
            SectionTitle(strings[R.string.camera])
            CameraSettings(strings)
            ColoredDivider()
        }

        item(contentType = "section") {
            SectionTitle(strings[R.string.scanner])
            ScannerSettings(strings)
            ColoredDivider()
        }

        item(contentType = "section") {
            SectionTitle(strings[R.string.about])
            AboutSettings(strings)
        }
    }
}

@Composable
fun ColoredDivider() =
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
internal fun ConnectionSettings(strings: SettingsStrings) {

    ComboBoxEnumPreference(
        title = strings[R.string.connection_mode],
        desc = strings[R.string.connection_mode_desc],
        icon = Icons.Default.PhonelinkSetup,
        values = strings.array(R.array.connection_mode_values),
        preference = PreferenceStore.CONNECTION_MODE
    )

    SwitchPreference(
        title = strings[R.string.auto_connect],
        desc = strings[R.string.auto_connect_desc],
        icon = Icons.Default.Link,
        preference = PreferenceStore.AUTO_CONNECT
    )

    /*SwitchPreference(
            title = strings[R.string.show_unnamed],
            desc = strings[R.string.show_unnamed_desc],
            icon = Icons.Default.DeviceUnknown,
            preference = PreferenceStore.SHOW_UNNAMED
    )*/

    SliderPreference(
        title = strings[R.string.send_delay],
        desc = strings[R.string.send_delay_desc],
        valueFormat = strings[R.string.send_delay_template],
        range = 0f..100f,
        icon = Icons.Default.Timer,
        preference = PreferenceStore.SEND_DELAY
    )

    ComboBoxEnumPreference(
        title = strings[R.string.keyboard_layout],
        desc = strings[R.string.keyboard_layout_desc],
        icon = Icons.Default.Keyboard,
        values = strings.array(R.array.keyboard_layout_values),
        preference = PreferenceStore.KEYBOARD_LAYOUT
    )

    val customKeysDialog = rememberDialogState()
    CustomKeysDialog(customKeysDialog)
    ButtonPreference(
        title = strings[R.string.custom_keys],
        desc = strings[R.string.define_custom_keys],
        icon = Icons.Default.KeyboardCommandKey,
        onClick = customKeysDialog::open
    )

    ComboBoxEnumPreference(
        title = strings[R.string.extra_keys],
        desc = strings[R.string.extra_keys_desc],
        icon = Icons.Default.AddCircle,
        values = strings.array(R.array.extra_keys_values),
        preference = PreferenceStore.EXTRA_KEYS
    )

    val errorString = strings.errorTemplate
    val codeRegex = remember { Regex("\\{[^{}]*CODE[^{}]*\\}") }
    TextBoxPreference(
        title = strings[R.string.custom_template],
        desc = strings[R.string.custom_templ_desc],
        descLong = strings[R.string.custom_templ_desc_long],
        validator = { template ->
            // Check if template contains at least one CODE placeholder (flexible format)
            if (!codeRegex.containsMatchIn(template))
                return@TextBoxPreference errorString

            // Check each CODE placeholder for conflicts
            val matches = codeRegex.findAll(template)
            for (match in matches) {
                val content = match.value.removePrefix("{").removeSuffix("}")
                val parts = content.split("_")

                // Check for duplicate components
                val uniqueParts = parts.toSet()
                if (uniqueParts.size != parts.size) {
                    val duplicates = parts.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                    return@TextBoxPreference strings.templateErrorDuplicates(duplicates.joinToString(", "))
                }
            }

            null
        },
        icon = Icons.Default.LibraryAdd,
        preference = PreferenceStore.TEMPLATE_TEXT
    )

    SwitchPreference(
        title = strings[R.string.templates_in_value],
        desc = strings[R.string.template_in_value_desc],
        icon = Icons.Default.Expand,
        preference = PreferenceStore.EXPAND_CODE
    )

    SwitchPreference(
        title = strings[R.string.auto_send],
        desc = strings[R.string.auto_send_desc],
        icon = Icons.AutoMirrored.Filled.Send,
        preference = PreferenceStore.AUTO_SEND
    )

    SwitchPreference(
        title = strings[R.string.send_with_volume_keys],
        desc = strings[R.string.send_vol_keys_desc],
        icon = Icons.AutoMirrored.Filled.VolumeMute,
        preference = PreferenceStore.SEND_WITH_VOLUME
    )

    val jsDialog = rememberDialogState()
    var jsEnabled by rememberPreferenceNull(PreferenceStore.ENABLE_JS)
    ButtonPreference(
        title = strings[R.string.custom_javascript],
        desc = strings[R.string.custom_js_desc],
        icon = Icons.Default.Code,
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalDivider(
                    Modifier
                        .height(32.dp)
                        .padding(horizontal = 24.dp)
                )
                jsEnabled?.let { c ->
                    Switch(c, onCheckedChange = {
                        jsEnabled = it
                    }, modifier = Modifier.semantics(mergeDescendants = true) {
                        stateDescription = "Custom JavaScript is ${if (c) "On" else "Off"}"
                    })
                }
            }
        },
        onClick = jsDialog::open
    )
    JavaScriptEditorDialog(jsDialog)
}

@Composable
internal fun AppearanceSettings(strings: SettingsStrings) {
    SwitchPreference(
        title = strings[R.string.keep_screen_on],
        desc = strings[R.string.keep_screen_on_desc],
        icon = Icons.Default.ScreenLockPortrait,
        preference = PreferenceStore.KEEP_SCREEN_ON
    )

    SwitchPreference(
        title = strings[R.string.allow_screen_rotation],
        desc = strings[R.string.allow_screen_rotation_desc],
        icon = Icons.Default.ScreenRotation,
        preference = PreferenceStore.ALLOW_SCREEN_ROTATION
    )

    SwitchPreference(
        title = strings[R.string.full_screen_scanner],
        desc = strings[R.string.full_screen_scanner_desc],
        icon = Icons.Default.Fullscreen,
        preference = PreferenceStore.SCANNER_FULL_SCREEN
    )

    ComboBoxEnumPreference(
        title = strings[R.string.theme],
        desc = strings[R.string.theme_desc],
        icon = Icons.Default.AutoFixHigh,
        values = strings.array(R.array.theme_values),
        preference = PreferenceStore.THEME
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchPreference(
            title = strings[R.string.dynamic_theme],
            desc = strings[R.string.dynamic_theme_desc],
            icon = Icons.Default.AutoAwesome,
            preference = PreferenceStore.DYNAMIC_THEME
        )
    }
}

@Composable
internal fun CameraSettings(strings: SettingsStrings) {
    SwitchPreference(
        title = strings[R.string.front_camera],
        desc = strings[R.string.front_camera_desc],
        icon = Icons.Default.FlipCameraAndroid,
        preference = PreferenceStore.FRONT_CAMERA
    )

    ComboBoxEnumPreference(
        title = strings[R.string.focus_mode],
        desc = strings[R.string.focus_mode_desc],
        values = strings.array(R.array.focus_mode_values),
        icon = Icons.Default.HdrAuto,
        preference = PreferenceStore.FOCUS_MODE
    )

    SwitchPreference(
        title = strings[R.string.fix_exposure],
        desc = strings[R.string.fix_exposure_desc],
        icon = Icons.Default.Exposure,
        preference = PreferenceStore.FIX_EXPOSURE
    )

    SwitchPreference(
        title = strings[R.string.preview_performance_mode],
        desc = strings[R.string.preview_mode_desc],
        icon = Icons.Default.Bolt,
        preference = PreferenceStore.PREVIEW_PERFORMANCE_MODE
    )

    ComboBoxEnumPreference(
        title = strings[R.string.scan_res],
        desc = strings[R.string.scan_res_desc],
        icon = Icons.Default.Hd,
        values = strings.array(R.array.scan_res_values),
        preference = PreferenceStore.SCAN_RESOLUTION
    )

    /*SwitchPreference(
            title = strings[R.string.auto_zoom],
            desc = strings[R.string.zooms_in_on_codes_to_far_away],
            icon = Icons.Default.ZoomIn,
            preference = PreferenceStore.AUTO_ZOOM
    )*/
}

@Composable
internal fun ScannerSettings(strings: SettingsStrings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    CheckBoxPreference(
        title = strings[R.string.code_types],
        desc = strings[R.string.code_types_desc_short],
        descLong = strings[R.string.code_types_desc],
        valueStrings = strings.array(R.array.code_types_values),
        icon = Icons.Default.QrCode2,
        preference = PreferenceStore.CODE_TYPES
    )

    AdvancedOptionsModal()

    SwitchPreference(
        title = strings[R.string.restrict_area],
        desc = strings[R.string.restrict_area_desc],
        icon = Icons.Default.CropFree,
        preference = PreferenceStore.RESTRICT_AREA
    )

    val errorString = strings.errorRegex
    TextBoxPreference(
        title = strings[R.string.filter_regex],
        desc = strings[R.string.filter_regex_desc],
        descLong = strings[R.string.filter_desc_long],
        validator = {
            val result = runCatching { it.toRegex() }
            if (!it.isBlank() && result.isFailure)
                return@TextBoxPreference result.exceptionOrNull()?.message ?: errorString
            null
        },
        icon = Icons.Default.FilterAlt,
        preference = PreferenceStore.SCAN_REGEX
    )

    ComboBoxEnumPreference(
        title = strings[R.string.overlay_type],
        desc = strings[R.string.overlay_type_desc],
        icon = Icons.Default.CenterFocusWeak,
        values = strings.array(R.array.overlay_values),
        preference = PreferenceStore.OVERLAY_TYPE,
        onReset = {
            scope.launch {
                context.setPreference(
                    PreferenceStore.OVERLAY_POS_X,
                    PreferenceStore.OVERLAY_POS_X.defaultValue
                )
                context.setPreference(
                    PreferenceStore.OVERLAY_POS_Y,
                    PreferenceStore.OVERLAY_POS_Y.defaultValue
                )
                context.setPreference(
                    PreferenceStore.OVERLAY_WIDTH,
                    PreferenceStore.OVERLAY_WIDTH.defaultValue
                )
                context.setPreference(
                    PreferenceStore.OVERLAY_HEIGHT,
                    PreferenceStore.OVERLAY_HEIGHT.defaultValue
                )
            }
        }
    )

    /*ComboBoxEnumPreference(
        title = strings[R.string.highlight_type],
        desc = strings[R.string.highlight_type_desc],
        icon = Icons.Default.Highlight,
        values = stringArrayResource(R.array.highlight_values),
        preference = PreferenceStore.HIGHLIGHT_TYPE
    )*/

    /*SwitchPreference(
        title = strings[R.string.show_possible],
        desc = strings[R.string.highlights_possible_codes],
        icon = Icons.Default.Highlight,
        preference = PreferenceStore.SHOW_POSSIBLE
    )*/

    SwitchPreference(
        title = strings[R.string.full_inside],
        desc = strings[R.string.full_inside_desc],
        icon = Icons.Default.QrCodeScanner,
        preference = PreferenceStore.FULL_INSIDE
    )

    ComboBoxEnumPreference(
        title = strings[R.string.scan_freq],
        desc = strings[R.string.scan_freq_desc],
        icon = Icons.Default.ShutterSpeed,
        values = strings.array(R.array.scan_freq_values),
        preference = PreferenceStore.SCAN_FREQUENCY
    )

    SwitchPreference(
        title = strings[R.string.play_sound],
        desc = strings[R.string.play_sound_desc],
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        preference = PreferenceStore.PLAY_SOUND
    )

    SwitchPreference(
        title = strings[R.string.haptic_feedback],
        desc = strings[R.string.haptic_feedback_desc],
        icon = Icons.Default.Vibration,
        preference = PreferenceStore.VIBRATE
    )

    /*SwitchPreference(
        title = strings[R.string.raw_value],
        desc = strings[R.string.raw_value_desc],
        icon = Icons.Default.Description,
        preference = PreferenceStore.RAW_VALUE
    )*/

    SwitchPreference(
        title = strings[R.string.clear_after_send],
        desc = strings[R.string.clear_after_send_desc],
        icon = Icons.Default.Clear,
        preference = PreferenceStore.CLEAR_AFTER_SEND
    )

    SwitchPreference(
        title = strings[R.string.private_mode],
        desc = strings[R.string.private_mode_desc],
        icon = Icons.Default.Shield,
        preference = PreferenceStore.PRIVATE_MODE,
    )

    SwitchPreference(
        title = strings[R.string.persistent_history],
        desc = strings[R.string.persistent_history_desc],
        icon = Icons.Default.Save,
        preference = PreferenceStore.PERSIST_HISTORY,
    )

    SaveScanImageOptionsModal()
}

@Composable
internal fun AboutSettings(strings: SettingsStrings) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    ButtonPreference(
        title = strings[R.string.source_code],
        desc = strings[R.string.source_code_desc],
        icon = Icons.Default.Code,
    ) {
        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner")
    }

    ButtonPreference(
        title = strings[R.string.report_issue],
        desc = strings[R.string.report_issue_desc],
        icon = Icons.Default.BugReport,
    ) {
        uriHandler.openUri("https://github.com/Fabi019/hid-barcode-scanner/issues/new")
    }

    ButtonPreference(
        title = strings[R.string.rate],
        desc = strings[R.string.rate_desc],
        icon = Icons.Default.Star,
    ) {
        uriHandler.openUri("market://details?id=${context.packageName}")
    }

    val shareVia = strings[R.string.share_via]
    ButtonPreference(
        title = strings[R.string.share],
        desc = strings[R.string.share_desc],
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
        title = strings[R.string.build_version],
        desc = strings.buildVersionDescription(
            BuildConfig.BUILD_TYPE,
            BuildConfig.VERSION_NAME,
            BuildConfig.GIT_COMMIT_HASH,
            BuildConfig.VERSION_CODE
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

// Cached strings to avoid repeated resource lookups during scroll
internal class SettingsStrings(private val context: Context) {
    private val stringCache = mutableMapOf<Int, String>()
    private val arrayCache = mutableMapOf<Int, Array<String>>()

    operator fun get(@StringRes id: Int): String =
        stringCache.getOrPut(id) { context.getString(id) }

    fun array(@ArrayRes id: Int): Array<String> =
        arrayCache.getOrPut(id) { context.resources.getStringArray(id) }

    val errorTemplate: String = this[R.string.template_error]
    val errorRegex: String = this[R.string.invalid_regex]

    fun templateErrorDuplicates(duplicates: String): String =
        context.getString(R.string.template_error_duplicates, duplicates)

    fun buildVersionDescription(
        buildType: String,
        versionName: String,
        commitHash: String,
        versionCode: Int
    ): String =
        context.getString(
            R.string.build_version_desc,
            buildType,
            versionName,
            commitHash,
            versionCode
        )
}
