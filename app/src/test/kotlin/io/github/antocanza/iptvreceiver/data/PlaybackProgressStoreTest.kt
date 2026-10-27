package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PlaybackProgressStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val directory get() = folder.root.resolve("progress")
    private val connection = ProviderConnection("Private account", "https://provider.example.test", "demo", "synthetic-secret")
    private val movie = WatchTarget(CatalogItem("42", "Private movie title", ContentType.MOVIE))
    private val point = PlaybackProgress(900_000, 6_600_000)
    private fun store() = FilePlaybackProgressStore(directory)

    @Test fun `fifteen minutes of a hundred ten minute movie survives restart independently of recent history`() {
        store().save(connection, movie, point)
        var time = 1_000L
        val history = FileWatchHistoryStore(folder.root.resolve("history")) { time }
        history.record(connection, movie)
        time += FileWatchHistoryStore.RETENTION_MILLIS
        assertTrue(history.load(connection).isEmpty())
        assertEquals(point, store().load(connection)[movie.key])
        assertTrue(store().load(connection).getValue(movie.key).canResume)
    }

    @Test fun `progress is isolated by server account movie show and episode while passwords can change`() {
        val show = CatalogItem("42", "Show", ContentType.SERIES)
        val first = WatchTarget(show, Episode("42", "Pilot", 1, 1, "mkv"))
        val next = first.copy(episode = Episode("43", "Next", 1, 2, "mkv"))
        val otherShow = first.copy(item = show.copy(id = "43"))
        val store = store()
        listOf(movie, first, next, otherShow).forEachIndexed { i, target -> store.save(connection, target, point.copy(positionMs = (i + 1) * 60_000L)) }
        assertEquals(listOf(60_000L, 120_000L, 180_000L, 240_000L), listOf(movie, first, next, otherShow).map { store().load(connection).getValue(it.key).positionMs })
        assertTrue(store.load(connection.with(username = "another")).isEmpty())
        assertTrue(store.load(connection.with(serverUrl = "https://other.example.test")).isEmpty())
        assertEquals(store.load(connection), store().load(connection.with(name = "Renamed", password = "changed", serverUrl = "https://PROVIDER.example.test:443/")))
        val bytes = directory.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1)
        listOf(connection.name, connection.password, connection.serverUrl, movie.item.title).forEach { assertFalse(bytes.contains(it)) }
    }

    @Test fun `late asynchronous checkpoints cannot overwrite a more recent seek or restart`() {
        val store = store()
        store.save(connection, movie, point, 100)
        store.save(connection, movie, PlaybackProgress(0, null), 200)
        store.save(connection, movie, point.copy(positionMs = 905_000), 150)
        assertEquals(0L, store().load(connection).getValue(movie.key).positionMs)
        store.save(connection, movie, point.copy(positionMs = 300_000), 300)
        assertEquals(300_000L, store().load(connection).getValue(movie.key).positionMs)
    }

    @Test fun `live and catchup never create movie progress`() {
        store().save(connection, WatchTarget(movie.item.copy(type = ContentType.LIVE)), point)
        assertTrue(store().load(connection).isEmpty())
        assertFalse(directory.exists())
    }

    @Test fun `completion invalid duration and resume thresholds are bounded`() {
        assertFalse(PlaybackProgress.checkpoint(4_999, null).canResume)
        assertTrue(PlaybackProgress.checkpoint(5_000, null).canResume)
        assertNull(PlaybackProgress.checkpoint(15_000, -9223372036854775807).durationMs)
        assertEquals(0L, PlaybackProgress.checkpoint(-1, 100_000).positionMs)
        assertFalse(PlaybackProgress.checkpoint(6_569_999, 6_600_000).completed)
        assertTrue(PlaybackProgress.checkpoint(6_570_000, 6_600_000).completed)
        assertFalse(PlaybackProgress.checkpoint(6_570_000, 6_600_000).canResume)
        assertTrue(PlaybackProgress.checkpoint(99_000, 100_000).completed)
        assertFalse(PlaybackProgress.checkpoint(98_999, 100_000).completed)
        assertTrue(PlaybackProgress.checkpoint(20_000, null, ended = true).completed)
        assertEquals(100_000L, PlaybackProgress.checkpoint(Long.MAX_VALUE, 100_000).positionMs)
    }

    @Test fun `corrupt and future progress versions are reported and preserved`() {
        val store = store()
        store.save(connection, movie, point)
        val file = directory.listFiles()!!.single()
        for (bytes in listOf(byteArrayOf(0, 0, 0, 2), byteArrayOf(1))) {
            file.writeBytes(bytes)
            assertThrows(IOException::class.java) { store.load(connection) }
            assertThrows(IOException::class.java) { store.save(connection, movie, point) }
            assertArrayEquals(bytes, file.readBytes())
        }
    }
}

private fun ProviderConnection.with(name: String = this.name, serverUrl: String = this.serverUrl, username: String = this.username, password: String = this.password) = ProviderConnection(name, serverUrl, username, password)
