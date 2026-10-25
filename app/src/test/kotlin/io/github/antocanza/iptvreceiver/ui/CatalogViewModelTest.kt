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
}

internal class FakeRepository : CatalogRepository {
    var fail = false
    var itemsRequested = false
    override suspend fun authenticate(connection: ProviderConnection) = Account("Active", null, listOf("m3u8"))
    override suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category> {
        if (fail) throw ProviderException(FailureReason.UNAVAILABLE)
        return listOf(Category("1", "Root", null), Category("2", "Child", "1"), Category("3", "Orphan", "404"))
    }
    override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String): List<CatalogItem> {
        itemsRequested = true
        return listOf(CatalogItem("11", "Sample", type))
    }
    override suspend fun movie(connection: ProviderConnection, item: CatalogItem) = MovieDetails(item, "Description")
    override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title, "", emptyList())
    override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String) = PlaybackSource("https://media.example.test/sample", item.title, true)
    override fun episodeStream(connection: ProviderConnection, episode: Episode) = PlaybackSource("https://media.example.test/sample", episode.title, false)
}
