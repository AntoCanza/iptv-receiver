package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.*

@Composable
internal fun CatalogScreen(state: CatalogState, type: ContentType, retry: () -> Unit,
    queryChanged: (String) -> Unit, showAll: (Boolean) -> Unit, retryItems: () -> Unit,
    openCategory: (String) -> Unit, openItem: (CatalogItem) -> Unit) {
    val query = state.query.trim()
    val configuration = LocalConfiguration.current
    val window = LocalWindowInfo.current.containerSize
    val compact = configuration.fontScale > 1.4f || with(LocalDensity.current) { window.height.toDp() < 480.dp }
    val allItemsVisible = state.canBrowseAll && (state.showAll || query.isNotBlank())
    var sortName by rememberSaveable { mutableStateOf(CatalogSort.PROVIDER.name) }
    var explicitSort by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = if (query.isNotBlank() && !explicitSort) CatalogSort.RELEVANCE
        else CatalogSort.valueOf(sortName).takeUnless { it == CatalogSort.RELEVANCE && query.isBlank() } ?: CatalogSort.PROVIDER
    val gridState = rememberSaveable(query, state.showAll, sort.name, genre, saver = LazyGridState.Saver) { LazyGridState() }
    val visibleKeys by remember(gridState) { derivedStateOf {
        gridState.layoutInfo.visibleItemsInfo.filter {
            it.offset.y < gridState.layoutInfo.viewportEndOffset && it.offset.y + it.size.height > gridState.layoutInfo.viewportStartOffset
        }.mapTo(mutableSetOf()) { it.key }
    } }
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val categories = remember(state.categories, query, allItemsVisible) {
        if (allItemsVisible) emptyList() else state.categories.filter { it.name.contains(query, true) }
    }
    val genres = remember(state.items) { state.items.flatMap { it.metadata.genres }.distinctBy { it.lowercase(java.util.Locale.ROOT) }.sorted() }
    val selectedGenre = genre?.takeIf { it in genres }
    val titles = remember(state.items, sort, query, selectedGenre, allItemsVisible) {
        if (state.canBrowseAll && !allItemsVisible) emptyList()
        else sortCatalog(state.items.filter { it.matchesCatalog(query, selectedGenre) }, sort, query)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val searchLabel = stringResource(when {
        !state.canBrowseAll -> R.string.search
        type == ContentType.LIVE -> R.string.search_all_channels
        type == ContentType.SERIES -> R.string.search_all_series
        else -> R.string.search_all_movies
    })
    // Search, refinements and results scroll together; no fixed header can consume the whole viewport.
    LazyVerticalGrid(columns = if (compact) GridCells.Fixed(1) else GridCells.Adaptive(144.dp),
        modifier = Modifier.fillMaxSize().imePadding(), state = gridState,
        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(state.query, queryChanged, Modifier.fillMaxWidth(),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = { if (state.query.isNotEmpty()) ActionIcon(R.drawable.ic_close, stringResource(R.string.clear_search)) { queryChanged("") } },
                label = { Text(searchLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus(); scope.launch { gridState.animateScrollToItem(1) } }))
        }
        if (allItemsVisible) item(key = "refinements", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(R.drawable.ic_folder, stringResource(R.string.browse_categories)) { focus.clearFocus(); keyboard?.hide(); showAll(false) }
                if (!state.itemsBusy && state.itemsError == null) Text(stringResource(R.string.catalog_result_count, titles.size),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                else Spacer(Modifier.weight(1f))
                if (state.items.isNotEmpty()) CatalogRefinements(sort,
                    listOfNotNull(CatalogSort.RELEVANCE.takeIf { query.isNotBlank() }) + listOf(CatalogSort.PROVIDER, CatalogSort.TITLE) +
                        listOfNotNull(CatalogSort.NEWEST.takeIf { state.items.any { it.addedAtSeconds != null } },
                            CatalogSort.RATING.takeIf { state.items.any { it.metadata.rating != null } }),
                    selectedGenre, genres,
                    onSort = { explicitSort = true; sortName = it.name }, onGenre = { genre = it })
            }
        } else if (state.items.any { it.type != ContentType.LIVE }) item(key = "refinements", span = { GridItemSpan(maxLineSpan) }) {
            CatalogRefinements(sort, listOfNotNull(CatalogSort.RELEVANCE.takeIf { query.isNotBlank() }) + CatalogSort.entries.filter { it != CatalogSort.RELEVANCE },
                selectedGenre, genres, onSort = { explicitSort = true; sortName = it.name }, onGenre = { genre = it })
        }
        if (state.canBrowseAll && !allItemsVisible) item(key = "all-items", span = { GridItemSpan(maxLineSpan) }) {
            NavigationCard(when (type) { ContentType.LIVE -> R.drawable.ic_live_tv; ContentType.SERIES -> R.drawable.ic_series; else -> R.drawable.ic_movie },
                stringResource(when (type) { ContentType.LIVE -> R.string.all_channels; ContentType.SERIES -> R.string.all_series; else -> R.string.all_movies })) { showAll(true) }
        }
        if (allItemsVisible && state.itemsBusy) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        if (allItemsVisible && state.itemsError != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(state.itemsError.localized(), color = MaterialTheme.colorScheme.error)
            Button(onClick = retryItems) { Text(stringResource(R.string.retry)) }
        }
        items(categories, key = { "category:${it.id}" }) { category ->
            Card(onClick = { openCategory(category.id) }, Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(20.dp))
                    Text(category.name.ifBlank { stringResource(R.string.unnamed_category) }, style = MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        items(titles, key = { "item:${it.id}" }) { item ->
            Card(onClick = { focus.clearFocus(); keyboard?.hide(); openItem(item) }, Modifier.fillMaxWidth()) {
                if (compact) Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatalogArtwork(item, Modifier.width(if (type == ContentType.LIVE) 72.dp else 48.dp), visible = "item:${item.id}" in visibleKeys)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (type != ContentType.LIVE) MetadataSummary(item.metadata, compact = true)
                    }
                } else {
                    CatalogArtwork(item, visible = "item:${item.id}" in visibleKeys)
                    Text(item.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (type != ContentType.LIVE) CatalogMetadata(item.metadata)
                }
            }
        }
        if (categories.isEmpty() && titles.isEmpty() && !state.itemsBusy && state.itemsError == null) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(if (query.isNotBlank() || selectedGenre != null) R.string.no_search_results else R.string.empty_category), Modifier.padding(12.dp))
        }
    }
}
