package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.model.Account
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.MovieDetails
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import io.github.antocanza.iptvreceiver.model.SeriesDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic-secret")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `root exposes root and orphan categories without loading all streams`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = CatalogViewModel(repository, connection, ContentType.LIVE, null)
        advanceUntilIdle()
        assertEquals(listOf("1", "3"), model.state.value.categories.map { it.id })
        assertFalse(repository.itemsRequested)
        assertFalse(model.state.value.busy)
    }

    @Test fun `branch shows both its child categories and directly assigned content`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, "1")
        advanceUntilIdle()
        assertEquals(listOf("2"), model.state.value.categories.map { it.id })
        assertEquals("11", model.state.value.items.single().id)
        assertTrue(repository.itemsRequested)
    }

    @Test fun `failed loads expose safe error and retry recovers`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { fail = true }
        val model = CatalogViewModel(repository, connection, ContentType.SERIES, null)
        advanceUntilIdle()
        assertEquals(FailureReason.UNAVAILABLE, model.state.value.error)
        repository.fail = false
        model.load()
        advanceUntilIdle()
        assertNull(model.state.value.error)
        assertEquals(2, model.state.value.categories.size)
    }

    @Test fun `movie root loads the whole catalog on search and reuses it across queries`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, null, dispatcher)
        advanceUntilIdle()
        assertTrue(model.state.value.canBrowseAllMovies)
        assertFalse(repository.itemsRequested)

        model.setQuery("Other")
        advanceUntilIdle()
        assertEquals(listOf("11", "22"), model.state.value.items.map { it.id })
        assertEquals(listOf<String?>(null), repository.requestedCategories)
        model.setQuery("Sample")
        model.setQuery("")
        model.showAllMovies(true)
        advanceUntilIdle()
        assertTrue(model.state.value.showAllMovies)
        assertEquals(1, repository.requestedCategories.size)
        model.showAllMovies(false)
        assertFalse(model.state.value.showAllMovies)
        assertEquals(listOf("1", "3"), model.state.value.categories.map { it.id })
    }

    @Test fun `all movies is available even when the provider has no categories`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { categoryList = emptyList() }
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, null, dispatcher)
        advanceUntilIdle()
        model.showAllMovies(true)
        advanceUntilIdle()
        assertEquals(2, model.state.value.items.size)
        assertEquals(listOf<String?>(null), repository.requestedCategories)
    }

    @Test fun `typing during full catalog loading keeps the latest query and makes one request`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply { itemsGate = gate }
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, null, dispatcher)
        advanceUntilIdle()
        model.setQuery("S")
        advanceUntilIdle()
        assertTrue(model.state.value.itemsBusy)
        model.setQuery("Sample")
        model.setQuery("")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("", model.state.value.query)
        assertFalse(model.state.value.itemsBusy)
        assertFalse(model.state.value.showAllMovies)
        assertEquals(1, repository.requestedCategories.size)
    }

    @Test fun `full catalog errors preserve categories and query and can be retried`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { failItems = true }
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, null, dispatcher)
        advanceUntilIdle()
        model.setQuery("Sample")
        advanceUntilIdle()
        assertEquals(FailureReason.UNAVAILABLE, model.state.value.itemsError)
        assertEquals("Sample", model.state.value.query)
        assertFalse(model.state.value.itemsBusy)
        assertEquals(2, model.state.value.categories.size)
        repository.failItems = false
        model.loadAllMovies()
        advanceUntilIdle()
        assertNull(model.state.value.itemsError)
        assertEquals(2, model.state.value.items.size)
    }

    @Test fun `search in a subcategory stays scoped to that category`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, "1", dispatcher)
        advanceUntilIdle()
        model.setQuery("Sample")
        model.showAllMovies(true)
        advanceUntilIdle()
        assertFalse(model.state.value.canBrowseAllMovies)
        assertFalse(model.state.value.showAllMovies)
        assertEquals(listOf("1"), repository.requestedCategories)
        assertEquals(listOf("11"), model.state.value.items.map { it.id })
    }

    @Test fun `refresh cancels an obsolete full catalog load and retains the search`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply { itemsGate = gate }
        val model = CatalogViewModel(repository, connection, ContentType.MOVIE, null, dispatcher)
        advanceUntilIdle()
        model.setQuery("Sample")
        advanceUntilIdle()
        repository.itemsGate = null
        model.load()
        advanceUntilIdle()
        assertEquals("Sample", model.state.value.query)
        assertFalse(model.state.value.itemsBusy)
        assertEquals(2, model.state.value.items.size)
        assertFalse(gate.isCompleted)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, repository.requestedCategories.size)
    }
}

internal class FakeRepository : CatalogRepository {
    var fail = false
    var itemsRequested = false
    var failItems = false
    var itemsGate: CompletableDeferred<Unit>? = null
    var categoryList = listOf(Category("1", "Root", null), Category("2", "Child", "1"), Category("3", "Orphan", "404"))
    val requestedCategories = mutableListOf<String?>()
    override suspend fun authenticate(connection: ProviderConnection) = Account("Active", null, listOf("m3u8"))
    override suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category> {
        if (fail) throw ProviderException(FailureReason.UNAVAILABLE)
        return categoryList
    }
    override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem> {
        itemsRequested = true
        requestedCategories += categoryId
        itemsGate?.await()
        if (failItems) throw ProviderException(FailureReason.UNAVAILABLE)
        val first = CatalogItem("11", "Sample", type)
        return if (categoryId == null) listOf(first, CatalogItem("22", "Other category film", type)) else listOf(first)
    }
    override suspend fun movie(connection: ProviderConnection, item: CatalogItem) = MovieDetails(item, "Description")
    override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title, "", emptyList())
    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String) = PlaybackSource("https://media.example.test/sample", item.title, true)
    override fun episodeStream(connection: ProviderConnection, episode: Episode) = PlaybackSource("https://media.example.test/sample", episode.title, false)
}
