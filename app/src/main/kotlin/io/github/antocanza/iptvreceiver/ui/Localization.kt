package io.github.antocanza.iptvreceiver.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.FailureReason

internal val supportedLanguages = linkedMapOf(
    "en" to "English", "it" to "Italiano", "es" to "Español", "fr" to "Français",
    "de" to "Deutsch", "pt" to "Português", "ar" to "العربية", "ru" to "Русский",
    "zh-Hans" to "简体中文", "ja" to "日本語",
)

@Composable
internal fun LanguageSelector() {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.language)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            supportedLanguages.forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    expanded = false
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                })
            }
        }
    }
}

@Composable
internal fun FailureReason.localized(): String = stringResource(when (this) {
    FailureReason.INVALID_SERVER -> R.string.err_invalid_server
    FailureReason.HTTPS_REQUIRED -> R.string.err_https_required
    FailureReason.CREDENTIALS_REQUIRED -> R.string.err_credentials_required
    FailureReason.AUTHENTICATION_FAILED -> R.string.err_authentication_failed
    FailureReason.INACTIVE_ACCOUNT -> R.string.err_inactive_account
    FailureReason.EXPIRED_ACCOUNT -> R.string.err_expired_account
    FailureReason.SELECT_EPISODE -> R.string.err_select_episode
    FailureReason.INVALID_STREAM -> R.string.err_invalid_stream
    FailureReason.UNREACHABLE -> R.string.err_unreachable
    FailureReason.REDIRECTED -> R.string.err_redirected
    FailureReason.ACCESS_DENIED -> R.string.err_access_denied
    FailureReason.UNAVAILABLE -> R.string.err_unavailable
    FailureReason.EMPTY_RESPONSE -> R.string.err_empty_response
    FailureReason.UNSUPPORTED_RESPONSE -> R.string.err_unsupported_response
    FailureReason.TOO_LARGE -> R.string.err_too_large
    FailureReason.RESPONSE_UNREADABLE -> R.string.err_response_unreadable
    FailureReason.SAVE_FAILED -> R.string.err_save_failed
    FailureReason.REMOVE_FAILED -> R.string.err_remove_failed
    FailureReason.INSECURE_STREAM -> R.string.err_insecure_stream
    FailureReason.UNKNOWN -> R.string.err_unknown
})
