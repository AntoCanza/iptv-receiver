package io.github.antocanza.iptvreceiver

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.os.LocaleListCompat
import androidx.core.content.edit
import io.github.antocanza.iptvreceiver.ui.LoginScreen
import io.github.antocanza.iptvreceiver.ui.ReceiverNavigation
import io.github.antocanza.iptvreceiver.ui.ReceiverViewModel

class MainActivity : AppCompatActivity() {
    private var previewIntent by mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        previewIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewIntent = intent
        val preferences = getSharedPreferences("language_defaults", MODE_PRIVATE)
        if (!preferences.getBoolean("device_language_policy_v1", false)) {
            // The starter build seeded English; remove that old default once on upgrade.
            val oldEnglishDefault = preferences.getBoolean("initialized", false) &&
                AppCompatDelegate.getApplicationLocales().toLanguageTags() == "en"
            preferences.edit { putBoolean("device_language_policy_v1", true) }
            if (oldEnglishDefault) AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        enableEdgeToEdge()
        val container = application as ReceiverApplication
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val preview = remember(previewIntent) { StorePreview.create(previewIntent) }
                key(preview != null) {
                    val repository = preview?.first ?: container.repository
                    val store = preview?.second ?: container.connectionStore
                    val model: ReceiverViewModel = viewModel(key = if (preview != null) "store-preview" else "receiver",
                        factory = viewModelFactory { initializer { ReceiverViewModel(repository, store, container.watchHistoryStore) } })
                    val state by model.state.collectAsStateWithLifecycle()
                    val session = state.session
                    if (session == null) LoginScreen(state, model::connect)
                    else ReceiverNavigation(repository, session, model)
                }
            }
        }
    }
}
