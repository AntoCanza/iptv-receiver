package io.github.antocanza.iptvreceiver.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.antocanza.iptvreceiver.R
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun NavigationCard(@DrawableRes icon: Int, title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(20.dp).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
internal fun SettingsScreen(account: () -> Unit, server: () -> Unit, parental: () -> Unit, viewing: () -> Unit, downloads: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NavigationCard(R.drawable.ic_account, stringResource(R.string.settings_account), account)
        NavigationCard(R.drawable.ic_server, stringResource(R.string.settings_server), server)
        NavigationCard(R.drawable.ic_lock, stringResource(R.string.parental_controls), parental)
        NavigationCard(R.drawable.ic_history, stringResource(R.string.watch_storage_title), viewing)
        NavigationCard(R.drawable.ic_download, stringResource(R.string.downloads), downloads)
        Card(Modifier.fillMaxWidth()) { LanguageSelector(asRow = true) }
    }
}

@Composable
internal fun ViewingDataSettingsScreen(state: ViewingDataState, clear: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.watch_storage_help))
        if (state.failed) Text(stringResource(R.string.watch_clear_error), color = MaterialTheme.colorScheme.error)
        if (state.cleared) Text(stringResource(R.string.watch_cleared))
        OutlinedButton(onClick = { confirming = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.watch_clear))
        }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.watch_clear)) }, text = { Text(stringResource(R.string.watch_clear_confirm)) },
        confirmButton = { TextButton(onClick = { confirming = false; clear() }) { Text(stringResource(R.string.watch_clear)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun AccountSettingsScreen(session: Session, signOut: () -> Unit) {
    val account = session.account
    val locale = LocalConfiguration.current.locales[0]
    val expiry = remember(account.expiresAtSeconds, locale) { formatProviderTime(account.expiresAtSeconds, locale) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SettingsValue(R.drawable.ic_account, stringResource(R.string.username), session.connection.username)
                SettingsValue(R.drawable.ic_info, stringResource(R.string.settings_status),
                    stringResource(if (account.isTrial == true) R.string.account_trial else R.string.account_active))
                expiry?.let { SettingsValue(R.drawable.ic_calendar, stringResource(R.string.settings_expiry), it) }
                account.maxConnections?.let { SettingsValue(R.drawable.ic_server, stringResource(R.string.settings_connection_limit), it.toString()) }
                account.activeConnections?.let { SettingsValue(R.drawable.ic_history,
                    stringResource(R.string.settings_active_connections), it.toString()) }
            }
        }
        OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_logout), contentDescription = null)
            Text(stringResource(R.string.sign_out), Modifier.padding(start = 8.dp))
        }
    }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.sign_out_confirm_title)) },
        text = { Text(stringResource(R.string.sign_out_confirm_message)) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; signOut() }) { Text(stringResource(R.string.sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun ServerSettingsScreen(session: Session) {
    val locale = LocalConfiguration.current.locales[0]
    val server = session.account.serverInfo
    val serverTime = remember(server, locale) { formatProviderTime(server.timeAtSignInSeconds, locale, server.timezone, includeTime = true) }
    val scheme = remember(session.connection) { runCatching { URI(session.connection.serverUrl).scheme?.uppercase(Locale.ROOT) }.getOrNull() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SettingsValue(R.drawable.ic_server, stringResource(R.string.settings_address), session.connection.serverUrl)
                scheme?.let { SettingsValue(if (it == "HTTPS") R.drawable.ic_lock else R.drawable.ic_server,
                    stringResource(R.string.settings_protocol), it) }
                server.timezone?.let { SettingsValue(R.drawable.ic_language, stringResource(R.string.settings_timezone), it) }
                if (serverTime != null || session.account.liveFormats.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null)
                        Text(stringResource(if (expanded) R.string.metadata_show_less else R.string.metadata_more_details), Modifier.padding(start = 8.dp))
                    }
                    if (expanded) {
                        serverTime?.let { SettingsValue(R.drawable.ic_clock, stringResource(R.string.settings_server_time), it) }
                        if (session.account.liveFormats.isNotEmpty()) SettingsValue(R.drawable.ic_live_tv, stringResource(R.string.settings_formats),
                            session.account.liveFormats.joinToString(" · ") { if (it == "ts") "MPEG-TS" else "HLS" })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsValue(@DrawableRes icon: Int, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

internal fun formatProviderTime(seconds: Long?, locale: Locale, timezone: String? = null, includeTime: Boolean = false): String? =
    seconds?.let { runCatching {
        val formatter = if (includeTime) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        formatter.withLocale(locale).format(Instant.ofEpochSecond(it).atZone(timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()))
    }.getOrNull() }
