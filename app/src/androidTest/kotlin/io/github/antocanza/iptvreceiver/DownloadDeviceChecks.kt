package io.github.antocanza.iptvreceiver

import android.content.Context
import android.os.SystemClock
import io.github.antocanza.iptvreceiver.data.*
import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Fictional native-encoded video only. The harness snapshots/restores the real account separately. */
internal class DownloadDeviceChecks(private val context: Context, private val passed: (String) -> Unit) {
    fun run(useFolder: Boolean = false) = runBlocking {
        val app = context.applicationContext as ReceiverApplication
        val downloads = app.downloads
        val connection = ProviderConnection("Download fixture", "https://download-fixture.example.test", "fixture-user", "fixture-password")
        val movie = CatalogItem("900001", "Offline download fixture", ContentType.MOVIE, extension = "mp4", categoryId = "fixture")
        val data = downloadVideoFixture(context)
        val ranges = AtomicInteger()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    try { socket.use {
                        val input = it.getInputStream().bufferedReader()
                        val lines = buildList { while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; add(line) } }
                        val offset = lines.firstOrNull { line -> line.startsWith("Range:", true) }?.substringAfter("bytes=")?.substringBefore('-')?.trim()?.toIntOrNull() ?: 0
                        if (offset > 0) ranges.incrementAndGet()
                        val out = it.getOutputStream()
                        val header = buildString {
                            append(if (offset > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                            append("Content-Type: video/mp4\r\nContent-Length: ${data.size - offset}\r\nETag: fixture-v1\r\nConnection: close\r\n")
                            if (offset > 0) append("Content-Range: bytes $offset-${data.size - 1}/${data.size}\r\n")
                            append("\r\n")
                        }
                        out.write(header.toByteArray())
                        var index = offset
                        while (index < data.size) { val count = minOf(16 * 1024, data.size - index); out.write(data, index, count); out.flush(); index += count; Thread.sleep(25) }
                    } } catch (_: Exception) { /* Client pause/cancel closes its socket. */ }
                }
            }
        }
        val repository = object : CatalogRepository {
            override suspend fun authenticate(connection: ProviderConnection) = Account("Active", null, emptyList())
            override suspend fun categories(connection: ProviderConnection, type: ContentType) = listOf(Category("fixture", "Fictional", null))
            override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?) = listOf(movie)
            override suspend fun movie(connection: ProviderConnection, item: CatalogItem) = MovieDetails(item, "")
            override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title, "", emptyList())
            override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String) = PlaybackSource("http://127.0.0.1:${server.localPort}/movie/fixture-user/fixture-password/${item.id}.mp4", item.title, false)
            override fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource = error("Not used")
        }
        try {
            downloads.rememberAccount(connection)
            if (!useFolder) downloads.setFolder(null) else {
                if (downloads.folder.value == null) {
                    val grant = context.contentResolver.persistedUriPermissions.first { android.provider.DocumentsContract.getTreeDocumentId(it.uri) == "primary:IPTV-Download-Checks" }
                    downloads.setFolder(grant.uri)
                }
                check(downloads.folder.value != null)
            }
            downloads.setWifiOnly(false)
            downloads.enqueue(connection, WatchTarget(movie), repository)
            fun first() = downloads.store.load().first { it.target.item.id == movie.id && it.account == accountKey(connection) }
            waitFor { first().status == DownloadStatus.DOWNLOADING && first().bytes > 0 }
            val id = first().id
            downloads.pause(id)
            check(first().status == DownloadStatus.PAUSED)
            SystemClock.sleep(300)
            check(first().status == DownloadStatus.PAUSED)
            check(!String(context.filesDir.resolve("download-requests/$id.bin").readBytes(), Charsets.ISO_8859_1).contains("fixture-password"))
            passed("background request is encrypted; pause rejects stale worker completion")
            downloads.resume(id)
            waitFor { first().status == DownloadStatus.READY }
            val ready = first()
            downloads.pause(id)
            check(first().status == DownloadStatus.READY)
            passed("a delayed pause cannot downgrade an already completed download")
            check(ready.bytes == data.size.toLong() && ready.total == data.size.toLong())
            check(ranges.get() > 0)
            check(!context.filesDir.resolve("download-requests/$id.bin").exists())
            val saved = if (ready.location.startsWith("content:")) context.contentResolver.openInputStream(android.net.Uri.parse(ready.location))!!.use { it.readBytes() }
                else java.io.File(android.net.Uri.parse(ready.location).path!!).readBytes()
            check(saved.contentEquals(data))
            passed("native background transfer resumes a validated range and persists exact completed bytes")
            check(runCatching { downloads.files.trustedUri("file://${context.filesDir}/parental-controls/settings.bin") }.isFailure)
            check(runCatching { downloads.files.trustedUri("content://untrusted.example/tree/root/document/root%2Ffile") }.isFailure)
            passed("local playback and deletion reject paths outside owned media or granted local folders")
            val point = PlaybackProgress(18_000, 120_000)
            downloads.saveProgress(ready, point, System.nanoTime())
            check(downloads.playback(id).second?.positionMs == 18_000L)
            passed("offline bookmarks share account-scoped viewing storage")
            val cancel = WatchTarget(movie.copy(id = "900002", title = "Cancelled fixture"))
            downloads.enqueue(connection, cancel, repository)
            waitFor { downloads.store.load().any { it.target.item.id == "900002" && it.bytes > 0 } }
            val cancelled = downloads.store.load().first { it.target.item.id == "900002" }
            downloads.remove(cancelled.id)
            SystemClock.sleep(300)
            check(downloads.store.get(cancelled.id) == null && !downloads.files.exists(cancelled.location, cancelled.bytes))
            passed("cancelling a running download removes its partial file and durable entry")
            val missing = WatchTarget(movie.copy(id = "900003", title = "Missing fixture"))
            downloads.enqueue(connection, missing, repository)
            waitFor { downloads.store.load().any { it.target.item.id == "900003" && it.status == DownloadStatus.READY } }
            val missingEntry = downloads.store.load().first { it.target.item.id == "900003" }
            downloads.files.delete(missingEntry.location)
            downloads.files.delete(missingEntry.location)
            downloads.refresh()
            check(downloads.store.get(missingEntry.id)?.status == DownloadStatus.MISSING)
            check(runCatching { downloads.playback(missingEntry.id) }.isFailure)
            passed("externally missing files are marked unavailable and cannot be played")
            context.cacheDir.resolve("download-check-id.txt").writeText(id)
            // The harness restores the original encrypted preference after testing.
            // A normal cold launch now has no provider login, proving independent offline access.
            ConnectionStore(context).clear()
            passed("completed library remains indexed when saved provider login is absent")
        } finally { server.close() }
    }

    private fun waitFor(timeout: Long = 60_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) { if (condition()) return; SystemClock.sleep(100) }
        error("Download device condition timed out")
    }

}
