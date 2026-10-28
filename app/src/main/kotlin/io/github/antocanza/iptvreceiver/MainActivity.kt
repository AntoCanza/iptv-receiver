package io.github.antocanza.iptvreceiver

import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import android.app.PictureInPictureUiState
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.antocanza.iptvreceiver.playback.PlaybackPictureInPicture
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import io.github.antocanza.iptvreceiver.ui.LoginDraftViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.os.LocaleListCompat
import androidx.core.content.edit
import io.github.antocanza.iptvreceiver.ui.LoginScreen
import io.github.antocanza.iptvreceiver.ui.ReceiverNavigation
import io.github.antocanza.iptvreceiver.ui.ReceiverViewModel
import io.github.antocanza.iptvreceiver.ui.DownloadsNavigation

class MainActivity : AppCompatActivity() {
    private var previewIntent by mutableStateOf<Intent?>(null)
    private var downloadsOpenRequest by mutableStateOf(0)
    val pictureInPicture by lazy { PlaybackPictureInPicture(this) }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pictureInPicture.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture.onModeChanged(isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (pipState.isTransitioningToPip) pictureInPicture.onEntering()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_downloads", false)) downloadsOpenRequest++
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
                        factory = viewModelFactory { initializer { ReceiverViewModel(repository, store, container.watchHistoryStore,
                            progressStore = container.playbackProgressStore, reminders = if (preview == null) container.subscriptionReminders else null,
                            persistenceScope = container.persistenceScope,
                            onAuthenticated = { if (preview == null) container.downloads.rememberAccount(it) },
                            onSignOut = { if (preview == null) container.downloads.signOut() }) } })
                    val draft: LoginDraftViewModel = viewModel(key = if (preview != null) "preview-login-draft" else "login-draft")
                    val state by model.state.collectAsStateWithLifecycle()
                    LaunchedEffect(state.session != null) { if (state.session != null) draft.clear() }
                    val session = state.session
                    val offlineAccount by container.downloads.account.collectAsStateWithLifecycle()
                    var downloadsPage by rememberSaveable { mutableStateOf<String?>(null) }
                    LaunchedEffect(previewIntent) {
                        if (previewIntent?.getBooleanExtra("open_downloads", false) == true) downloadsPage = "downloads"
                    }
                    if (downloadsPage != null) DownloadsNavigation(container, checkNotNull(downloadsPage), session,
                        openListRequest = downloadsOpenRequest, back = { downloadsPage = null })
                    else if (session == null) LoginScreen(state, model::connect, draft,
                        openDownloads = if (offlineAccount != null) ({ downloadsPage = "downloads" }) else null)
                    else ReceiverNavigation(repository, session, model, openDownloads = { settings -> downloadsPage = if (settings) "download_settings" else "downloads" })
                }
            }
        }
    }
}
