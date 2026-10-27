package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.Episode

/** Prefer the last playback's position, then the next unwatched regular episode; specials stay selectable. */
internal fun continueEpisode(item: CatalogItem, episodes: List<Episode>, progress: Map<List<String>, PlaybackProgress>): Episode? {
    val regular = episodes.filter { it.season > 0 }.ifEmpty { episodes }
        .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number }.thenBy { it.id })
    fun point(episode: Episode) = progress[WatchTarget(item, episode).key]
    val latest = regular.filter { point(it)?.lastPlayedAtMillis != null }
        .maxByOrNull { point(it)?.lastPlayedAtMillis ?: 0 }
    if (latest != null) {
        if (point(latest)?.completed != true) return latest
        regular.drop(regular.indexOf(latest) + 1).firstOrNull { point(it)?.watched != true }?.let { return it }
    } else {
        regular.firstOrNull { point(it)?.canResume == true }?.let { return it }
    }
    return regular.firstOrNull { point(it)?.watched != true } ?: latest ?: regular.firstOrNull()
}

@Composable
internal fun WatchProgressSummary(progress: PlaybackProgress?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (progress?.watched == true) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(when {
                progress?.watched == true && progress.canResume -> R.string.watch_rewatching
                progress?.watched == true -> R.string.watch_watched
                progress != null && progress.positionMs > 0 -> R.string.watch_in_progress
                else -> R.string.watch_unwatched
            }), style = MaterialTheme.typography.labelLarge)
        }
        if (progress != null) {
            val duration = progress.durationMs
            if (duration != null) {
                LinearProgressIndicator(progress = { (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.history_progress, formatWatchTime(progress.positionMs), formatWatchTime(duration)),
                    style = MaterialTheme.typography.bodySmall)
                if (progress.canResume) Text(stringResource(R.string.watch_remaining, formatWatchTime((duration - progress.positionMs).coerceAtLeast(0))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (progress.positionMs > 0) Text(stringResource(R.string.resume_message_unknown, formatWatchTime(progress.positionMs)),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun watchPlayLabel(progress: PlaybackProgress?): String = when {
    progress?.canResume == true -> stringResource(R.string.watch_resume_at, formatWatchTime(progress.positionMs))
    progress?.watched == true -> stringResource(R.string.watch_again)
    else -> stringResource(R.string.play)
}
