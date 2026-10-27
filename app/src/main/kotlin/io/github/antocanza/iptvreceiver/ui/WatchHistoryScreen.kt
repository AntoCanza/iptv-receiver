package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.FileWatchHistoryStore
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.ContentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RefreshWatchHistory(model: ReceiverViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                model.refreshHistory().join()
                val expiration = model.history.value.entries.minOfOrNull {
                    it.watchedAtMillis + FileWatchHistoryStore.retentionMillis(it.target.item.type)
                }
                val remaining = expiration?.let { it - System.currentTimeMillis() }
                delay(remaining?.coerceIn(1L, 60_000L) ?: 60_000L)
            }
        }
    }
}

@Composable
internal fun WatchHistoryScreen(state: HistoryState, retry: () -> Unit, play: (WatchTarget) -> Unit, details: (WatchTarget) -> Unit,
    loadMore: () -> Unit = {}) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.watch_history_help), Modifier.padding(vertical = 16.dp))
        }
        if (state.busy) item { CircularProgressIndicator() }
        if (state.failed) item {
            Text(stringResource(R.string.watch_history_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
        if (!state.busy && !state.failed && state.entries.isEmpty()) item {
            Text(stringResource(R.string.watch_history_empty), Modifier.padding(vertical = 24.dp))
        }
        items(state.entries, key = { it.target.key.joinToString(separator = "\u0000") }) { entry ->
            val target = entry.target
            Card(onClick = { play(target) }, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(target.item.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium)
                    target.episode?.let { episode ->
                        Text(stringResource(R.string.season_number, episode.season) + " · " +
                            stringResource(R.string.episode_number, episode.number, episode.title))
                    }
                    Text(target.item.type.label() + " · " + dateFormat.format(Date(entry.watchedAtMillis)),
                        style = MaterialTheme.typography.bodySmall)
                    entry.progress?.let { progress ->
                        WatchProgressSummary(progress)
                    }
                    Row {
                        val title = target.episode?.title ?: target.item.title
                        val playLabel = stringResource(if (entry.progress?.canResume == true) R.string.resume_action else R.string.play)
                        ActionIcon(R.drawable.ic_play, stringResource(R.string.action_for_title, playLabel, title)) { play(target) }
                        if (target.item.type != ContentType.LIVE) ActionIcon(R.drawable.ic_info,
                            stringResource(R.string.action_for_title, stringResource(R.string.watch_history_details), title)) { details(target) }
                    }
                }
            }
        }
        if (state.hasMore) item {
            TextButton(onClick = loadMore, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.watch_load_more))
            }
        }
    }
}
