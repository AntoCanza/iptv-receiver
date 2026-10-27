package io.github.antocanza.iptvreceiver.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
internal fun LanguageSelector(asRow: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        if (asRow) {
            val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.ic_language), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    Text(supportedLanguages[selected] ?: stringResource(R.string.language_system), style = MaterialTheme.typography.bodySmall)
                }
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
            }
        } else IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_language), contentDescription = stringResource(R.string.language))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.language_system)) }, trailingIcon = {
                RadioButton(AppCompatDelegate.getApplicationLocales().isEmpty, onClick = null)
            }, onClick = {
                expanded = false
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            })
            supportedLanguages.forEach { (tag, name) ->
                DropdownMenuItem(text = { Text(name) }, trailingIcon = {
                    RadioButton(AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag, onClick = null)
                }, onClick = {
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
    FailureReason.CREDENTIALS_REQUIRED -> R.string.err_credentials_required
    FailureReason.AUTHENTICATION_FAILED -> R.string.err_authentication_failed
    FailureReason.LOGIN_FAILED -> R.string.err_login_failed
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
    FailureReason.PARENTAL_BLOCKED -> R.string.parental_blocked
    FailureReason.UNKNOWN -> R.string.err_unknown
})
