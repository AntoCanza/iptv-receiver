package io.github.antocanza.iptvreceiver.data

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentMetadata
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.IOException

class DownloadStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun entry(id: String = "a".repeat(64), account: String = "b".repeat(64)) = DownloadEntry(id, account,
        WatchTarget(CatalogItem("1", "Fictional movie", ContentType.MOVIE, extension = "mp4", categoryId = "child")),
        listOf(Category("parent", "Drama", null), Category("child", "Films", "parent")), "file:///private/download-media/$id.mp4")

    @Test fun `index survives reopening with bytes states and validators`() {
        val file = temporary.root.resolve("downloads/index.bin")
        val store = DownloadStore(file)
        val entry = entry().copy(status = DownloadStatus.PAUSED, bytes = 12_345, total = 99_999, validator = "opaque-etag")
        store.put(entry)
        assertEquals(entry, DownloadStore(file).get(entry.id))
        assertEquals(1, store.entries.value.size)
    }

    @Test fun `stopped worker cannot update a new generation or recreate a removed file`() {
        val store = DownloadStore(temporary.root.resolve("index.bin"))
        val original = entry()
        store.put(original)
        store.update(original.id) { it.copy(generation = "new", status = DownloadStatus.PAUSED) }
        assertNull(store.update(original.id, original.generation) { it.copy(status = DownloadStatus.READY) })
        assertEquals(DownloadStatus.PAUSED, store.get(original.id)?.status)
        store.remove(original.id)
        assertNull(store.update(original.id, "new") { it.copy(bytes = 10) })
        assertTrue(store.load().isEmpty())
    }

    @Test fun `account type and episode identities stay separate`() {
        val store = DownloadStore(temporary.root.resolve("index.bin"))
        store.put(entry())
        store.put(entry("c".repeat(64), "d".repeat(64)))
        val series = entry("e".repeat(64)).copy(target = WatchTarget(CatalogItem("1", "Series", ContentType.SERIES, categoryId = "child"), Episode("1", "Episode", 2, 3, "mkv")))
        store.put(series)
        assertEquals(3, store.load().size)
        assertEquals(series, DownloadStore(temporary.root.resolve("index.bin")).get(series.id))
    }

    @Test fun `provider artwork trailer and response fields never reach the index`() {
        val file = temporary.root.resolve("index.bin")
        val entry = entry().let { it.copy(target = WatchTarget(it.target.item.copy(imageUrl = "https://secret.invalid/password",
            imageFallbackUrls = listOf("https://secret.invalid/alternate"), metadata = ContentMetadata(trailerUrl = "https://secret.invalid/trailer")))) }
        DownloadStore(file).put(entry)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("secret.invalid"))
        assertNull(DownloadStore(file).get(entry.id)?.target?.item?.imageUrl)
    }

    @Test fun `unknown corrupt or truncated indexes remain intact and fail visibly`() {
        val file = temporary.root.resolve("index.bin")
        listOf(byteArrayOf(1, 2, 3), byteArrayOf(0, 0, 0, 99), byteArrayOf(0, 0, 0, 1, -1, -1, -1, -1)).forEach { corrupt ->
            file.writeBytes(corrupt)
            assertThrows(Exception::class.java) { DownloadStore(file).put(entry()) }
            assertArrayEquals(corrupt, file.readBytes())
        }
    }

    @Test fun `ready state requires a nonempty complete length and cannot exceed a known total`() {
        val store = DownloadStore(temporary.root.resolve("index.bin"))
        listOf(entry().copy(status = DownloadStatus.READY), entry().copy(status = DownloadStatus.READY, bytes = 5, total = 10),
            entry().copy(bytes = 11, total = 10)).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { store.put(invalid) }
        }
        assertTrue(store.load().isEmpty())
    }

    @Test fun `offline parental checks use current rules and stored ancestor classification`() {
        val entry = entry()
        assertTrue(entry.allowed(ParentalPolicy()))
        assertFalse(entry.allowed(ParentalPolicy(storageFailed = true)))
        assertFalse(entry.allowed(ParentalPolicy(blockedCategories = setOf("${entry.account}:MOVIE:parent"))))
        val adult = entry.copy(categories = listOf(Category("parent", "Adult", null), Category("child", "Films", "parent")))
        assertFalse(adult.allowed(ParentalPolicy()))
        assertTrue(adult.allowed(ParentalPolicy(blockAdult = false)))
        assertTrue(entry.allowed(ParentalPolicy(blockedCategories = setOf("${"f".repeat(64)}:MOVIE:parent"))))
    }

    @Test fun `live adaptive and incomplete series targets cannot be downloaded as files`() {
        val base = entry().target
        assertEquals("mp4", downloadExtension(base))
        assertNull(downloadExtension(base.copy(item = base.item.copy(type = ContentType.LIVE))))
        assertNull(downloadExtension(base.copy(item = base.item.copy(type = ContentType.SERIES))))
        listOf("m3u8", "mpd", "../../mp4", "").forEach { assertNull(downloadExtension(base.copy(item = base.item.copy(extension = it)))) }
    }

    @Test fun `ancestor snapshot handles missing parents and cycles without unrelated categories`() {
        val all = listOf(Category("a", "A", "b"), Category("b", "B", "a"), Category("x", "X", null))
        assertEquals(listOf("a", "b"), categoryAncestors(all, "a").map { it.id })
        assertTrue(categoryAncestors(all, null).isEmpty())
    }
}
