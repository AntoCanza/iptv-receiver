package io.github.antocanza.iptvreceiver.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentMetadata
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.artworkUrls

@Composable
internal fun DetailsScreen(
    state: DetailsState,
    item: CatalogItem,
    retry: () -> Unit,
    playMovie: (CatalogItem) -> Unit,
    playEpisode: (Episode) -> Unit,
) {
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val series = state.series
    val movie = state.movie
    val metadata = movie?.item?.metadata ?: series?.metadata ?: item.metadata
    val title = movie?.item?.title ?: series?.title ?: item.title
    val poster = movie?.item?.artworkUrls ?: series?.imageUrls?.ifEmpty { item.artworkUrls } ?: item.artworkUrls
    val description = movie?.description ?: series?.description.orEmpty()
    val seasons = remember(series) { series?.episodes?.map { it.season }?.distinct().orEmpty() }
    var selectedSeason by rememberSaveable(item.id) { mutableStateOf<Int?>(null) }
    val seasonNumber = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val seasonInfo = series?.seasons?.firstOrNull { it.number == seasonNumber }
    val episodes = remember(series, seasonNumber) { series?.episodes?.filter { it.season == seasonNumber }.orEmpty() }
    val listState = rememberLazyListState()
    val visibleKeys by remember { derivedStateOf {
        listState.layoutInfo.visibleItemsInfo.filter {
            it.offset < listState.layoutInfo.viewportEndOffset && it.offset + it.size > listState.layoutInfo.viewportStartOffset
        }.mapTo(mutableSetOf()) { it.key }
    } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (movie != null && metadata.backdropUrls.isNotEmpty()) item(key = "backdrop") {
                ProviderArtwork(metadata.backdropUrls, Modifier.clip(MaterialTheme.shapes.medium),
                    visible = "backdrop" in visibleKeys, ratio = 2.4f)
            }
            item(key = "heading") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProviderArtwork(poster, Modifier.width(104.dp).clip(MaterialTheme.shapes.medium),
                        visible = "heading" in visibleKeys, ratio = 2f / 3)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleLarge)
                        MetadataSummary(metadata, compact = series != null)
                        if (metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (description.isNotBlank()) item(key = "description") { Synopsis(description) }
            if (metadata.cast != null || metadata.director != null || metadata.country != null) {
                item(key = "credits") { Credits(metadata) }
            }
            if (series != null && seasons.isNotEmpty()) {
                item(key = "season-picker") {
                    var choosing by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        Box {
                            TextButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(seasonInfo?.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.season_number, seasonNumber ?: 0), Modifier.weight(1f))
                                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
                            }
                            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                                seasons.forEach { number ->
                                    val name = series.seasons.firstOrNull { it.number == number }?.name?.takeIf { it.isNotBlank() }
                                    DropdownMenuItem(text = { Text(name ?: stringResource(R.string.season_number, number)) },
                                        onClick = { selectedSeason = number; choosing = false },
                                        trailingIcon = { RadioButton(selected = seasonNumber == number, onClick = null) })
                                }
                            }
                        }
                        Text(stringResource(R.string.season_episode_count, episodes.size), style = MaterialTheme.typography.labelLarge)
                        seasonInfo?.description?.takeIf { it.isNotBlank() }?.let { Synopsis(it) }
                    }
                }
                items(episodes, key = { "episode:${it.id}" }) { episode ->
                    Card(onClick = { playEpisode(episode) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (episode.imageUrls.isNotEmpty()) ProviderArtwork(episode.imageUrls,
                                Modifier.width(80.dp).clip(MaterialTheme.shapes.small), visible = "episode:${episode.id}" in visibleKeys)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.episode_number, episode.number,
                                    episode.title.ifBlank { stringResource(R.string.untitled) }), style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                episode.durationSeconds?.let { Text(stringResource(R.string.metadata_minutes, (it + 59) / 60),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (episode.description.isNotBlank()) Text(episode.description,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.play),
                                modifier = Modifier.size(24.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (series != null && series.episodes.isEmpty()) item { Text(stringResource(R.string.empty_category)) }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
        // A full-width primary action remains within reach while the synopsis/credits scroll.
        if (movie != null || metadata.trailerUrl != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (movie != null) Button(onClick = { playMovie(movie.item) }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                Text(stringResource(R.string.play), Modifier.padding(start = 8.dp))
            }
            metadata.trailerUrl?.let { TrailerButton(it) }
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column {
        Text(text, maxLines = if (expanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
        if (overflows || expanded) TextButton(onClick = { expanded = !expanded }) {
            Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null)
            Text(stringResource(if (expanded) R.string.metadata_show_less else R.string.metadata_read_more), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Credits(metadata: ContentMetadata) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null)
            Text(stringResource(if (expanded) R.string.metadata_show_less else R.string.metadata_more_details), Modifier.padding(start = 8.dp))
        }
        if (expanded) {
            metadata.director?.let { Text(stringResource(R.string.metadata_director, it)) }
            metadata.cast?.let { Text(stringResource(R.string.metadata_cast, it)) }
            metadata.country?.let { Text(stringResource(R.string.metadata_country, it)) }
        }
    }
}

@Composable
private fun TrailerButton(url: String) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val unavailable = stringResource(R.string.metadata_trailer_unavailable)
    TextButton(onClick = {
        try { handler.openUri(url) } catch (_: IllegalArgumentException) { Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show() }
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_trailer), contentDescription = null)
        Text(stringResource(R.string.metadata_watch_trailer), Modifier.padding(start = 8.dp))
    }
}
