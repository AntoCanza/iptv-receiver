package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.ContentMetadata

@Composable
internal fun MetadataSummary(metadata: ContentMetadata, modifier: Modifier = Modifier, compact: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (metadata.year != null || metadata.rating != null) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            metadata.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            metadata.rating?.let { rating ->
                val value = String.format(locale, "%.1f", rating)
                val description = stringResource(R.string.metadata_rating, value)
                Row(Modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(R.drawable.ic_star), contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!compact) metadata.durationSeconds?.let { seconds ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.metadata_minutes, (seconds + 59) / 60), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun CatalogMetadata(metadata: ContentMetadata) {
    if (metadata.year != null || metadata.rating != null) MetadataSummary(metadata,
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), compact = true)
}

@Composable
internal fun CatalogRefinements(
    sort: CatalogSort,
    sorts: List<CatalogSort>,
    genre: String?,
    genres: List<String>,
    onSort: (CatalogSort) -> Unit,
    onGenre: (String?) -> Unit,
) {
    var sorting by remember { mutableStateOf(false) }
    var filtering by remember { mutableStateOf(false) }
    val sortDescription = stringResource(R.string.catalog_sort, sort.label())
    val genreDescription = stringResource(R.string.catalog_genre_filter, genre ?: stringResource(R.string.catalog_all_genres))
    Row {
        Box {
            ActionIcon(R.drawable.ic_sort, sortDescription) { sorting = true }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                sorts.forEach { option -> DropdownMenuItem(text = { Text(option.label()) },
                    onClick = { sorting = false; onSort(option) }, trailingIcon = { RadioButton(selected = sort == option, onClick = null) },
                    modifier = Modifier.semantics { selected = sort == option }) }
            }
        }
        if (genres.isNotEmpty()) Box {
            ActionIcon(R.drawable.ic_filter, genreDescription, selected = genre != null) { filtering = true }
            DropdownMenu(expanded = filtering, onDismissRequest = { filtering = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.catalog_all_genres)) }, onClick = { filtering = false; onGenre(null) },
                    trailingIcon = { RadioButton(selected = genre == null, onClick = null) })
                genres.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { filtering = false; onGenre(option) },
                    trailingIcon = { RadioButton(selected = genre == option, onClick = null) }) }
            }
        }
    }
}

@Composable
private fun CatalogSort.label(): String = stringResource(when (this) {
    CatalogSort.RELEVANCE -> R.string.catalog_sort_relevance
    CatalogSort.PROVIDER -> R.string.catalog_sort_provider
    CatalogSort.TITLE -> R.string.catalog_sort_title
    CatalogSort.NEWEST -> R.string.catalog_sort_newest
    CatalogSort.RATING -> R.string.catalog_sort_rating
})
