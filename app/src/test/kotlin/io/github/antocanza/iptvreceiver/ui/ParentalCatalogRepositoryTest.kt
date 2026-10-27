package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.data.*
import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ParentalCatalogRepositoryTest {
    @Test fun `guide and archive enforce category policy and reject fabricated programmes`() = runTest {
        val controls = ParentalControls(folder.root.resolve("guide-policy.bin"))
        val programme = Programme("p", "Fixture", "", 10, 20, true, "2026-10-26 10:00:00")
        var guideCalls = 0
        val provider = object : CatalogRepository by source() {
            override suspend fun guide(connection: ProviderConnection, item: CatalogItem): List<Programme> { guideCalls++; return listOf(programme) }
            override fun catchupStream(connection: ProviderConnection, item: CatalogItem, programme: Programme) = PlaybackSource("https://example.test/fixture.ts", programme.title, false)
        }
        val repository = ParentalCatalogRepository(provider, controls)
        repository.items(connection, ContentType.LIVE, null)
        assertTrue(runCatching { repository.guide(connection, adult) }.isFailure)
        assertEquals(0, guideCalls)
        assertEquals(listOf(programme), repository.guide(connection, normal))
        assertNotNull(repository.catchupStream(connection, normal, programme))
        assertTrue(runCatching { repository.catchupStream(connection, normal, programme.copy(id = "forged")) }.isFailure)
        val access = controls.create("parent-password")
        controls.update(access, category = categoryKey(connection, ContentType.LIVE, "safe"), blocked = true)
        assertTrue(runCatching { repository.catchupStream(connection, normal, programme) }.isFailure)
        assertTrue(runCatching { repository.guide(connection, normal) }.isFailure)
        assertEquals(1, guideCalls)
    }
    @get:Rule val folder = TemporaryFolder()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic")
    private val categories = listOf(Category("safe", "News", null), Category("adult", "XXX", null), Category("child", "Other", "adult"))
    private val normal = CatalogItem("1", "News channel", ContentType.LIVE, categoryId = "safe")
    private val adult = CatalogItem("2", "Anonymous channel", ContentType.LIVE, categoryId = "child")
    private val flagged = CatalogItem("3", "Other channel", ContentType.LIVE, categoryId = "safe", isAdult = true)
    private fun source(): CatalogRepository = object : CatalogRepository by FakeRepository() {
        override suspend fun categories(connection: ProviderConnection, type: ContentType) = categories
        override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?) = listOf(normal, adult, flagged)
            .map { it.copy(type = type) }.filter { categoryId == null || it.categoryId == categoryId }
        override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title, "", listOf(Episode("11", "Pilot", 1, 1, "mp4")))
    }

    @Test fun `default guard filters categories descendants search history and denies direct streams`() = runTest {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        val repository = ParentalCatalogRepository(source(), controls)
        assertEquals(listOf("safe"), repository.categories(connection, ContentType.LIVE).map { it.id })
        assertEquals(listOf(normal), repository.items(connection, ContentType.LIVE, null))
        assertTrue(repository.items(connection, ContentType.LIVE, "child").isEmpty())
        assertTrue(runCatching { repository.authorize(connection, adult) }.isFailure)
        assertTrue(runCatching { repository.stream(connection, adult) }.isFailure)
        assertNotNull(repository.stream(connection, normal))
        val old = listOf(normal, adult, flagged).map { WatchHistoryEntry(WatchTarget(it.copy(categoryId = null, isAdult = false)), 1) }
        val restarted = ParentalCatalogRepository(source(), controls)
        assertEquals(listOf("1"), restarted.visibleHistory(connection, old).map { it.target.item.id })
        assertTrue(runCatching { restarted.movie(connection, adult.copy(type = ContentType.MOVIE)) }.isFailure)
        assertTrue(runCatching { restarted.series(connection, adult.copy(type = ContentType.SERIES)) }.isFailure)
    }

    @Test fun `new policy immediately applies to cached items and reset reinstates adult blocking`() = runTest {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        val access = controls.create("parent-password")
        controls.update(access, blockAdult = false)
        val repository = ParentalCatalogRepository(source(), controls)
        assertEquals(3, repository.items(connection, ContentType.LIVE, null).size)
        assertNotNull(repository.stream(connection, adult))
        controls.update(access, category = categoryKey(connection, ContentType.LIVE, "safe"), blocked = true)
        assertTrue(runCatching { repository.stream(connection, normal) }.isFailure)
        controls.reset()
        assertTrue(runCatching { repository.stream(connection, adult) }.isFailure)
        assertNotNull(repository.stream(connection, normal))
    }

    @Test fun `series episode playback rechecks parent restrictions and historic episode ownership`() = runTest {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        val repository = ParentalCatalogRepository(source(), controls)
        val series = normal.copy(type = ContentType.SERIES)
        val episode = Episode("11", "Pilot", 1, 1, "mp4")
        repository.authorizeEpisode(connection, WatchTarget(series, episode))
        assertNotNull(repository.episodeStream(connection, episode))
        assertTrue(runCatching { repository.authorizeEpisode(connection, WatchTarget(series, episode.copy(id = "999"))) }.isFailure)
        val access = controls.create("parent-password")
        controls.update(access, category = categoryKey(connection, ContentType.SERIES, "safe"), blocked = true)
        assertTrue(runCatching { repository.episodeStream(connection, episode) }.isFailure)
    }

    @Test fun `guard failure keeps history private when provider metadata cannot be loaded`() = runTest {
        val controls = ParentalControls(folder.root.resolve("settings.bin"))
        val source = object : CatalogRepository by source() {
            override suspend fun categories(connection: ProviderConnection, type: ContentType): List<Category> = throw ProviderException(FailureReason.UNAVAILABLE)
        }
        val repository = ParentalCatalogRepository(source, controls)
        assertTrue(runCatching { repository.visibleHistory(connection, listOf(WatchHistoryEntry(WatchTarget(normal), 1))) }.isFailure)
        assertTrue(runCatching { repository.stream(connection, normal) }.isFailure)
    }

    @Test fun `removed history identifiers do not redownload the complete catalog on every refresh`() = runTest {
        var requests = 0
        val source = object : CatalogRepository by source() {
            override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?): List<CatalogItem> {
                requests++
                return listOf(normal)
            }
        }
        val repository = ParentalCatalogRepository(source, ParentalControls(folder.root.resolve("settings.bin")))
        val entries = listOf(WatchHistoryEntry(WatchTarget(normal.copy(id = "removed")), 1))
        repeat(3) { assertTrue(repository.visibleHistory(connection, entries).isEmpty()) }
        assertEquals(1, requests)
        assertTrue(runCatching { repository.authorize(connection, entries.single().target.item) }.isFailure)
        assertEquals(1, requests)
        repository.authenticate(connection)
        repository.visibleHistory(connection, entries)
        assertEquals(2, requests)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `reset immediately removes adult entries from the receiver history without deleting durable records`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val controls = ParentalControls(folder.root.resolve("settings.bin"))
            val access = controls.create("parent-password")
            controls.update(access, blockAdult = false)
            val history = FileWatchHistoryStore(folder.root.resolve("history"))
            history.record(connection, WatchTarget(adult))
            val store = object : SavedConnectionStore {
                override fun load() = connection
                override fun save(connection: ProviderConnection) = Unit
                override fun clear() = Unit
            }
            val model = ReceiverViewModel(ParentalCatalogRepository(source(), controls), store, history, dispatcher)
            advanceUntilIdle()
            assertEquals(1, model.history.value.entries.size)
            controls.reset()
            advanceUntilIdle()
            assertTrue(model.history.value.entries.isEmpty())
            assertFalse(model.history.value.failed)
            assertEquals(1, history.load(connection).size)
        } finally { Dispatchers.resetMain() }
    }
}
