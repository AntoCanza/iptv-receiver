package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.data.FilePlaybackProgressStore
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "secret")
    private val show = CatalogItem("show", "Show", ContentType.SERIES)
    private val episodes = listOf(Episode("special", "Special", 0, 1, "mp4"), Episode("a", "First", 1, 1, "mp4"),
        Episode("b", "Second", 1, 2, "mp4"), Episode("c", "Third", 2, 1, "mp4"))
    private fun key(episode: Episode) = WatchTarget(show, episode).key
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `continue follows chronological episodes rather than provider ordering and skips specials`() {
        assertEquals(episodes[1], continueEpisode(show, episodes.reversed(), emptyMap()))
        assertEquals(episodes[0], continueEpisode(show, listOf(episodes[0]), emptyMap()))
        assertNull(continueEpisode(show, emptyList(), emptyMap()))
    }

    @Test fun `continue uses last played episode and advances across seasons after completion`() {
        val points = mutableMapOf(key(episodes[1]) to PlaybackProgress(60_000, 100_000, lastPlayedAtMillis = 100),
            key(episodes[2]) to PlaybackProgress(70_000, 100_000, lastPlayedAtMillis = 200))
        assertEquals(episodes[2], continueEpisode(show, episodes, points))
        points[key(episodes[2])] = PlaybackProgress(100_000, 100_000, completed = true, lastPlayedAtMillis = 300)
        assertEquals(episodes[3], continueEpisode(show, episodes, points))
    }

    @Test fun `manual watched marks and legacy resume points work without invented play dates`() {
        val points = mutableMapOf(key(episodes[1]) to PlaybackProgress(0, null, completed = true))
        assertEquals(episodes[2], continueEpisode(show, episodes, points))
        points[key(episodes[3])] = PlaybackProgress(60_000, null)
        assertEquals(episodes[3], continueEpisode(show, episodes, points))
    }

    @Test fun `a briefly started episode remains the next episode even before it becomes resumable`() {
        val points = mapOf(key(episodes[2]) to PlaybackProgress(1_000, 100_000, lastPlayedAtMillis = 100))
        assertEquals(episodes[2], continueEpisode(show, episodes, points))
        assertFalse(points.getValue(key(episodes[2])).canResume)
    }

    @Test fun `completed series allows rewatch and unrelated show progress cannot influence continuation`() {
        val points = episodes.associate { key(it) to PlaybackProgress(0, null, completed = true) }
        assertEquals(episodes[1], continueEpisode(show, episodes, points))
        assertEquals(episodes[1], continueEpisode(show.copy(id = "another"), episodes, points))
    }

    @Test fun `details load progress without recording a watch and manual edits survive reopening`() = runTest(dispatcher) {
        val storage = FilePlaybackProgressStore(folder.root.resolve("progress"))
        val repository = object : CatalogRepository by FakeRepository() {
            override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails("Show", "", episodes)
        }
        val details = DetailsViewModel(repository, connection, show, storage, dispatcher)
        advanceUntilIdle()
        assertTrue(details.progress.value.entries.isEmpty())
        details.setWatched(episodes[1], true); advanceUntilIdle()
        assertTrue(details.progress.value.entries.getValue(key(episodes[1])).watched)
        assertNull(storage.read(connection, WatchTarget(show, episodes[1]))!!.lastPlayedAtMillis)
        val reopened = DetailsViewModel(repository, connection, show, storage, dispatcher)
        advanceUntilIdle()
        assertEquals(details.progress.value.entries, reopened.progress.value.entries)
        reopened.setWatched(episodes[1], false); advanceUntilIdle()
        assertFalse(reopened.progress.value.entries.getValue(key(episodes[1])).watched)
        reopened.setWatched(episodes[1].copy(id = "not-in-series"), true); advanceUntilIdle()
        assertEquals(1, reopened.progress.value.entries.size)
    }

    @Test fun `unreadable progress is reported without showing unknown titles as unwatched and retry recovers`() = runTest(dispatcher) {
        val storage = FilePlaybackProgressStore(folder.root.resolve("progress"))
        val target = WatchTarget(CatalogItem("42", "Movie", ContentType.MOVIE))
        storage.save(connection, target, PlaybackProgress(60_000, 100_000))
        val file = folder.root.resolve("progress").listFiles()!!.single()
        val original = file.readBytes(); file.writeBytes(byteArrayOf(1))
        val details = DetailsViewModel(FakeRepository(), connection, target.item, storage, dispatcher)
        advanceUntilIdle()
        assertTrue(details.progress.value.failed)
        details.setWatched(null, false); advanceUntilIdle()
        assertArrayEquals(byteArrayOf(1), file.readBytes())
        file.writeBytes(original)
        details.refreshProgress(); advanceUntilIdle()
        assertFalse(details.progress.value.failed)
        assertTrue(details.progress.value.entries.getValue(target.key).canResume)
    }
}
