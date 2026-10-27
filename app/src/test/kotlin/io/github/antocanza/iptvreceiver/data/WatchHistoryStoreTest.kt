package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class WatchHistoryStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val connection = ProviderConnection("Test", "https://provider.example.test", "demo", "synthetic-password")
    private val movie = WatchTarget(CatalogItem("42", "A movie", ContentType.MOVIE, extension = "mp4"))
    private val channel = WatchTarget(CatalogItem("42", "A channel", ContentType.LIVE))
    private var now = TimeUnit.DAYS.toMillis(100)
    private val directory get() = folder.root.resolve("files/watch-history")
    private fun store() = FileWatchHistoryStore(directory) { now }

    @Test fun `latest watches come first and rewatching moves an entry without duplication`() {
        val store = store()
        store.record(connection, movie)
        now += 1_000
        store.record(connection, channel)
        now += 1_000
        val entries = store.record(connection, movie)
        assertEquals(listOf(movie, channel), entries.map { it.target })
        assertEquals(now, entries.first().watchedAtMillis)
        assertEquals(entries, store().load(connection))
    }

    @Test fun `ten day cutoff is rolling and exact and rewatch renews retention`() {
        val store = store()
        store.record(connection, movie)
        now += 1
        store.record(connection, channel)
        now += FileWatchHistoryStore.RETENTION_MILLIS - 1
        assertEquals(listOf(channel), store.load(connection).map { it.target })
        store.record(connection, channel)
        now += FileWatchHistoryStore.RETENTION_MILLIS - 1
        assertEquals(1, store.load(connection).size)
        now += 1
        assertTrue(store.load(connection).isEmpty())
        assertTrue(store().load(connection).isEmpty())
    }

    @Test fun `history is scoped to account and server and survives connection rename or password change`() {
        val store = store()
        store.record(connection, movie)
        assertTrue(store.load(ProviderConnection("Test", connection.serverUrl, "other", "secret")).isEmpty())
        assertTrue(store.load(ProviderConnection("Test", "https://other.example.test", "demo", "secret")).isEmpty())
        val renamed = ProviderConnection("Renamed", "https://PROVIDER.example.test:443/", "demo", "new-secret")
        assertEquals(listOf(movie), store.load(renamed).map { it.target })
    }

    @Test fun `episodes with colliding stream IDs remain independent of other shows and content types`() {
        val first = WatchTarget(CatalogItem("42", "Show one", ContentType.SERIES), Episode("42", "Pilot", 1, 1, "mkv"))
        val second = WatchTarget(CatalogItem("43", "Show two", ContentType.SERIES), Episode("42", "Pilot", 1, 1, "mp4"))
        val next = first.copy(episode = Episode("43", "Next", 1, 2, "mp4"))
        val store = store()
        listOf(movie, channel, first, second, next).forEach { store.record(connection, it); now++ }
        assertEquals(listOf(next, second, first, channel, movie), store().load(connection).map { it.target })
    }

    @Test fun `temporary file maintenance preserves durable history across restart`() {
        val cache = folder.root.resolve("cache")
        listOf("tmp", "temporary").forEach { name ->
            cache.resolve("$name/old.part").apply { parentFile!!.mkdirs(); writeText("temporary"); setLastModified(1) }
        }
        store().record(connection, movie)
        listOf("tmp", "temporary").forEach { assertEquals(1, pruneExpiredTempFiles(cache.resolve(it), now)) }
        store().pruneExpired()
        assertEquals(listOf(movie), store().load(connection).map { it.target })
    }

    @Test fun `maintenance expires history for accounts that are not currently signed in`() {
        val other = ProviderConnection("Other", connection.serverUrl, "other", "secret")
        val store = store()
        store.record(other, movie)
        now += 1_000
        store.record(connection, channel)
        now += FileWatchHistoryStore.RETENTION_MILLIS - 1_000
        store.pruneExpired()
        assertTrue(store().load(other).isEmpty())
        assertEquals(listOf(channel), store().load(connection).map { it.target })
    }

    @Test fun `stored metadata excludes artwork URLs provider addresses and account credentials`() {
        val unsafe = movie.copy(item = movie.item.copy(imageUrl = "https://poster.invalid/synthetic-password",
            imageFallbackUrls = listOf("https://poster.invalid/second-secret")))
        val entries = store().record(connection, unsafe)
        assertEquals(listOf(movie), entries.map { it.target })
        val file = directory.listFiles()!!.single()
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        listOf("synthetic-password", "second-secret", "provider.example.test", "poster.invalid", "demo").forEach {
            assertFalse(bytes.contains(it))
            assertFalse(file.name.contains(it))
        }
        assertEquals(entries, store().load(connection))
    }

    @Test(expected = IOException::class) fun `unsupported schema is reported without overwriting durable data`() {
        val store = store()
        store.record(connection, movie)
        val file = directory.listFiles()!!.single()
        DataOutputStream(file.outputStream()).use { it.writeInt(999); it.writeInt(0) }
        try { store.load(connection) } finally {
            assertEquals(999, java.io.DataInputStream(file.inputStream()).use { it.readInt() })
        }
    }
}
