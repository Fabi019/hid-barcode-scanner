package dev.fabik.bluetoothhid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fabik.bluetoothhid.ui.SettingsDropdown
import dev.fabik.bluetoothhid.ui.theme.BluetoothHIDTheme
import dev.fabik.bluetoothhid.ui.theme.configureWindow
import dev.fabik.bluetoothhid.ui.tooltip
import dev.fabik.bluetoothhid.utils.LocalDataStore
import dev.fabik.bluetoothhid.utils.ProfileManager
import kotlinx.coroutines.runBlocking

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Configure window for high refresh rate and transparency
        configureWindow(window)

        // Load saved active profile before first composition to avoid a flash of wrong settings
        runBlocking { ProfileManager.initialize(applicationContext) }

        setContent {
            val activeDataStore by remember { ProfileManager.activeStoreFlow(applicationContext) }
                .collectAsStateWithLifecycle(initialValue = ProfileManager.currentStore(applicationContext))

            CompositionLocalProvider(LocalDataStore provides activeDataStore) {
                BluetoothHIDTheme(window = window) {
                    Surface(Modifier.fillMaxSize()) {
                        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

                        Scaffold(
                            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                            topBar = {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.settings)) },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = { finishAfterTransition() },
                                            Modifier.tooltip(stringResource(R.string.back))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                        }
                                    },
                                    actions = {
                                        SettingsDropdown()
                                    },
                                    scrollBehavior = scrollBehavior
                                )
                            }
                        ) { padding ->
                            Box(Modifier.padding(padding)) {
                                SettingsContent()
                            }
                        }
                    }
                }
            }
        }
    }

}
