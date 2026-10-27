package io.github.antocanza.iptvreceiver.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class ResumeRequest(val target: WatchTarget, val progress: PlaybackProgress, val resolve: () -> PlaybackSource)

internal fun formatWatchTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    return if (minutes < 60) "$minutes:$tail" else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail"
}

@Composable
internal fun ResumeDialog(progress: PlaybackProgress, cancel: () -> Unit, resume: () -> Unit, restart: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text(stringResource(R.string.resume_title)) },
        text = { Text(progress.durationMs?.let { stringResource(R.string.resume_message, formatWatchTime(progress.positionMs), formatWatchTime(it)) }
            ?: stringResource(R.string.resume_message_unknown, formatWatchTime(progress.positionMs))) },
        confirmButton = { TextButton(onClick = resume) { Text(stringResource(R.string.resume_action)) } },
        dismissButton = { TextButton(onClick = restart) { Text(stringResource(R.string.restart_action)) } })
}

@Composable
internal fun SubscriptionReminder(model: ReceiverViewModel, session: Session, allowed: Boolean) {
    val notice by model.expiryNotice.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(model, session, owner, allowed) {
        if (allowed) owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { model.checkExpiryReminder().join(); delay(60_000) }
        }
    }
    if (allowed) notice?.let { value ->
        val locale = LocalConfiguration.current.locales[0]
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(value.date)
        AlertDialog(onDismissRequest = model::acknowledgeExpiryReminder,
            title = { Text(stringResource(R.string.subscription_reminder_title)) },
            text = { Text(stringResource(if (value.expired) R.string.subscription_expired else R.string.subscription_expires, date) +
                if (!value.expired) "\n" + stringResource(R.string.subscription_days, value.daysRemaining) else "") },
            confirmButton = { TextButton(onClick = model::acknowledgeExpiryReminder) { Text(stringResource(R.string.acknowledge)) } })
    }
}
