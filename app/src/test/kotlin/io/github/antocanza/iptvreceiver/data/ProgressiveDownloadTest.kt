package io.github.antocanza.iptvreceiver.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException

class ProgressiveDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun output(file: File) = object : DownloadOutput {
        override fun length() = file.length()
        override fun open(offset: Long) = FileOutputStream(file, true).apply { channel.truncate(offset); channel.position(offset) }
    }
    private fun run(server: MockWebServer, file: File, validator: String? = null, total: Long? = null,
        check: () -> Unit = {}, progress: (Long, Long?, String?) -> Unit = { _, _, _ -> }) =
        ProgressiveDownload().transfer(server.url("/movie/fictional-user/fictional-password/1.mp4").toString(), output(file), validator, total, check, progress)

    @Test fun `complete transfer writes exact bytes and reports final size`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("synthetic-video-bytes").setHeader("ETag", "v1"))
            val file = temporary.newFile()
            var lastBytes = 0L; var lastTotal: Long? = null; var lastValidator: String? = null
            run(server, file, progress = { bytes, total, validator -> lastBytes = bytes; lastTotal = total; lastValidator = validator })
            assertEquals("synthetic-video-bytes", file.readText())
            assertEquals(file.length(), lastBytes); assertEquals(file.length(), lastTotal); assertEquals("v1", lastValidator)
            assertEquals("identity", server.takeRequest().getHeader("Accept-Encoding"))
        }
    }

    @Test fun `validated range resume preserves prefix and requests only the remainder`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "v1").setBody("def"))
            val file = temporary.newFile().apply { writeText("abc") }
            run(server, file, "v1", 6)
            assertEquals("abcdef", file.readText())
            val request = server.takeRequest()
            assertEquals("bytes=3-", request.getHeader("Range")); assertEquals("v1", request.getHeader("If-Range"))
        }
    }

    @Test fun `server ignoring range restarts and truncates an old longer tail`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("new"))
            val file = temporary.newFile().apply { writeText("old-long-file") }
            run(server, file, "v1", 100)
            assertEquals("new", file.readText())
        }
    }

    @Test fun `missing or weak validators cause a full restart`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("fresh").setHeader("ETag", "W/weak"))
            val file = temporary.newFile().apply { writeText("stale") }
            var stored: String? = "unused"
            run(server, file, progress = { _, _, validator -> stored = validator })
            assertEquals("fresh", file.readText()); assertNull(stored); assertNull(server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun `interruption while replacing a validator cannot retain an old file prefix`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("replacement-video").setHeader("ETag", "new-version"))
            val file = temporary.newFile().apply { writeText("old-prefix") }
            assertThrows(CancellationException::class.java) {
                run(server, file, "old-version", 100, progress = { bytes, _, validator ->
                    if (bytes == 0L && validator == "new-version") throw CancellationException("Simulated interruption")
                })
            }
            assertEquals(0, file.length())
        }
    }

    @Test fun `wrong range changed validator or changed total never append unsafe bytes`() {
        listOf(Triple("bytes 2-5/6", "v1", 6L), Triple("bytes 3-5/6", "v2", 6L), Triple("bytes 3-5/6", "v1", 9L)).forEach { (range, etag, total) ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", range).setHeader("ETag", etag).setBody("def"))
                val file = temporary.newFile().apply { writeText("abc") }
                assertThrows(IOException::class.java) { run(server, file, "v1", total) }
                assertEquals("abc", file.readText())
            }
        }
    }

    @Test fun `adaptive manifests and error pages never become completed videos`() {
        listOf("#EXTM3U\n#EXT-X-STREAM-INF:...", "<html>failure</html>", "{\"error\":true}").forEach { body ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body).setHeader("Content-Type", "application/octet-stream"))
                val file = temporary.newFile()
                assertThrows(UnsupportedDownloadException::class.java) { run(server, file) }
                assertEquals(0, file.length())
            }
        }
    }

    @Test fun `HTTP redirects are supported but local URI redirects are rejected`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/delivered.mp4"))
            server.enqueue(MockResponse().setBody("video"))
            val file = temporary.newFile(); run(server, file)
            assertEquals("video", file.readText())
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "file:///private/config"))
            assertThrows(UnsupportedDownloadException::class.java) { run(server, file) }
        }
    }

    @Test fun `permanent access failures are explicit and cancellation propagates`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val file = temporary.newFile()
            assertThrows(DownloadAccessException::class.java) { run(server, file) }
            assertThrows(CancellationException::class.java) { run(server, file, check = { throw CancellationException() }) }
        }
    }

    @Test fun `empty and truncated files cannot complete`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(""))
            assertThrows(IOException::class.java) { run(server, temporary.newFile()) }
            server.enqueue(MockResponse().setBody("abc").setHeader("Content-Length", 10).setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END))
            assertThrows(IOException::class.java) { run(server, temporary.newFile()) }
        }
    }

    @Test fun `range parser rejects overflow and malformed lengths`() {
        assertEquals(Triple(0L, 9L, 10L), parseDownloadRange("bytes 0-9/10"))
        listOf(null, "bytes 5-3/6", "bytes 0-10/10", "bytes 0-5/*", "bytes 0-999999999999999999999/10").forEach { assertNull(parseDownloadRange(it)) }
    }
}
