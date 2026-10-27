package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.MovieDetails
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.SeriesDetails
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.PlaybackProgressStore
import io.github.antocanza.iptvreceiver.data.WatchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogState(
    val busy: Boolean = true,
    val categories: List<Category> = emptyList(),
    val items: List<CatalogItem> = emptyList(),
    val title: String = "",
    val error: FailureReason? = null,
    val query: String = "",
    val canBrowseAll: Boolean = false,
    val showAll: Boolean = false,
    val itemsBusy: Boolean = false,
    val itemsError: FailureReason? = null,
)

class CatalogViewModel(
    private val repository: CatalogRepository,
    private val connection: ProviderConnection,
    private val type: ContentType,
    private val categoryId: String?,
    private val catalogDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val canBrowseAll = categoryId == null
    private val mutableState = MutableStateFlow(CatalogState(canBrowseAll = canBrowseAll))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var itemsJob: Job? = null
    private var allItems: List<CatalogItem>? = null
    init { load() }

    fun load() {
        job?.cancel()
        itemsJob?.cancel()
        allItems = null
        job = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null, items = emptyList(),
                itemsBusy = false, itemsError = null)
            try {
                val categories = repository.categories(connection, type)
                val categoryIds = categories.mapTo(mutableSetOf(), Category::id)
                val children = categories.filter {
                    if (categoryId == null) it.parentId == null || it.parentId !in categoryIds
                    else it.parentId == categoryId
                }
                val items = if (categoryId == null) emptyList() else repository.items(connection, type, categoryId)
                mutableState.value = mutableState.value.copy(busy = false, categories = children, items = items,
                    title = categories.firstOrNull { it.id == categoryId }?.name.orEmpty())
                if (canBrowseAll && (mutableState.value.showAll || mutableState.value.query.isNotBlank())) {
                    loadAllItems()
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(busy = false, error = safeMessage(error))
            }
        }
    }

    fun setQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        if (canBrowseAll && query.isNotBlank() && mutableState.value.itemsError == null) loadAllItems()
    }

    fun showAll(show: Boolean) {
        if (!canBrowseAll) return
        mutableState.value = mutableState.value.copy(showAll = show, query = "")
        if (show) loadAllItems()
    }

    fun loadAllItems() {
        if (!canBrowseAll || mutableState.value.busy || itemsJob?.isActive == true) return
        allItems?.let {
            mutableState.value = mutableState.value.copy(items = it)
            return
        }
        mutableState.value = mutableState.value.copy(itemsBusy = true, itemsError = null)
        itemsJob = viewModelScope.launch {
            try {
                // Mapping a complete provider catalog must not block typing on the main thread.
                val items = withContext(catalogDispatcher) { repository.items(connection, type, null) }
                allItems = items
                mutableState.value = mutableState.value.copy(items = items, itemsBusy = false)
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(itemsBusy = false, itemsError = safeMessage(error))
            }
        }
    }
}

data class DetailsState(
    val busy: Boolean = true,
    val movie: MovieDetails? = null,
    val series: SeriesDetails? = null,
    val error: FailureReason? = null,
)

data class DetailProgressState(
    val busy: Boolean = true,
    val entries: Map<List<String>, PlaybackProgress> = emptyMap(),
    val failed: Boolean = false,
    val saving: Boolean = false,
)

class DetailsViewModel(
    private val repository: CatalogRepository,
    private val connection: ProviderConnection,
    private val item: CatalogItem,
    private val progressStore: PlaybackProgressStore? = null,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DetailsState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var progressJob: Job? = null
    private val mutableProgress = MutableStateFlow(DetailProgressState())
    val progress = mutableProgress.asStateFlow()
    init { load(); refreshProgress() }

    fun refreshProgress(): Job? {
        if (mutableProgress.value.saving) return null
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            try {
                val entries = withContext(storageDispatcher) { progressStore?.forItem(connection, item).orEmpty() }
                mutableProgress.value = DetailProgressState(busy = false, entries = entries)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableProgress.value = mutableProgress.value.copy(busy = false, failed = true) }
        }
        return progressJob
    }

    fun setWatched(episode: Episode?, watched: Boolean) {
        val destination = progressStore ?: return
        if (mutableProgress.value.saving || mutableProgress.value.busy || mutableProgress.value.failed) return
        if (item.type == ContentType.SERIES && state.value.series?.episodes?.none { it == episode } != false) return
        if (item.type == ContentType.MOVIE && (episode != null || state.value.movie == null)) return
        val target = WatchTarget(item, episode)
        val observed = System.nanoTime()
        progressJob?.cancel()
        mutableProgress.value = mutableProgress.value.copy(saving = true)
        progressJob = viewModelScope.launch {
            try {
                val entries = withContext(storageDispatcher) {
                    destination.setWatched(connection, target, watched, observed)
                    destination.forItem(connection, item)
                }
                mutableProgress.value = DetailProgressState(busy = false, entries = entries)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableProgress.value = mutableProgress.value.copy(saving = false, failed = true) }
        }
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.value = DetailsState()
            try {
                mutableState.value = when (item.type) {
                    ContentType.MOVIE -> DetailsState(false, movie = repository.movie(connection, item))
                    ContentType.SERIES -> DetailsState(false, series = repository.series(connection, item))
                    ContentType.LIVE -> DetailsState(false)
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) {
                mutableState.value = DetailsState(busy = false, error = safeMessage(error))
            }
        }
    }
}
