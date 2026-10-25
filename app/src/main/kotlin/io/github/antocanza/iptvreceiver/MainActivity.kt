package io.github.antocanza.iptvreceiver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("language_defaults", MODE_PRIVATE)
        if (!preferences.getBoolean("initialized", false)) {
            preferences.edit { putBoolean("initialized", true) }
            if (AppCompatDelegate.getApplicationLocales().isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                return
            }
        }
        enableEdgeToEdge()
        val container = application as ReceiverApplication
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val model: ReceiverViewModel = viewModel(factory = viewModelFactory {
                    initializer { ReceiverViewModel(container.repository, container.connectionStore) }
                })
                val state by model.state.collectAsStateWithLifecycle()
                val session = state.session
                if (session == null) LoginScreen(state, model::connect)
                else ReceiverNavigation(container.repository, session, model)
            }
        }
    }
}
