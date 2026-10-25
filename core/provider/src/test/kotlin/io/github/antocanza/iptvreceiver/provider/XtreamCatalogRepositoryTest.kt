package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamCatalogRepositoryTest {
    private val connection = ProviderConnection("Test", "https://provider.example.test:8443/base/", "user+one", "test/&?+ value")
    private var requested: HttpUrl? = null
    private fun repository(response: String): XtreamCatalogRepository = XtreamCatalogRepository(JsonTransport { url ->
        requested = url
        Json.parseToJsonElement(response)
    })

    @Test fun `login accepts numeric or string fields and encodes credentials correctly`() = runTest {
        val account = repository("""{"user_info":{"auth":"1","status":"Active","exp_date":null,"allowed_output_formats":["m3u8","ts","rtmp"]}}""")
            .authenticate(connection)
        assertEquals(listOf("m3u8", "ts"), account.liveFormats)
        assertNull(account.expiresAtSeconds)
        assertEquals("/base/player_api.php", requested?.encodedPath)
        assertEquals(connection.username, requested?.queryParameter("username"))
        assertEquals(connection.password, requested?.queryParameter("password"))
    }

    @Test fun `login rejects invalid credentials inactive accounts and expired accounts`() = runTest {
        for (response in listOf(
            """{"user_info":{"auth":0}}""",
            """{"user_info":{"auth":1,"status":"Disabled"}}""",
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1"}}""",
        )) {
            val result = runCatching { repository(response).authenticate(connection) }
            assertTrue(result.exceptionOrNull() is ProviderException)
        }
    }

    @Test fun `plain HTTP and URLs containing credentials or queries are rejected before transport`() = runTest {
        for (url in listOf("http://provider.example.test", "https://user:secret@provider.example.test", "https://provider.example.test?password=secret")) {
            requested = null
            val result = runCatching { repository("{}").authenticate(ProviderConnection("Test", url, "demo", "test")) }
            assertTrue(result.exceptionOrNull() is ProviderException)
            assertNull(requested)
        }
    }

    @Test fun `categories retain hierarchy and normalize root parents`() = runTest {
        val categories = repository("""[{"category_id":1,"category_name":"Root","parent_id":0},{"category_id":"2","category_name":"Child","parent_id":"1"}]""")
            .categories(connection, ContentType.MOVIE)
        assertNull(categories[0].parentId)
        assertEquals("1", categories[1].parentId)
        assertEquals("get_vod_categories", requested?.queryParameter("action"))
    }

    @Test fun `series list uses series id and correct provider action`() = runTest {
        val items = repository("""[{"series_id":"42","name":"Sample series","cover":null}]""")
            .items(connection, ContentType.SERIES, "7")
        assertEquals("42", items.single().id)
        assertNull(items.single().imageUrl)
        assertEquals("get_series", requested?.queryParameter("action"))
        assertEquals("7", requested?.queryParameter("category_id"))
    }

    @Test fun `series episodes are sorted numerically and use episode id for playback`() = runTest {
        val item = CatalogItem("55", "Sample", ContentType.SERIES)
        val series = repository("""{"info":{"name":"Sample"},"episodes":{"10":[{"id":"101","episode_num":"2","season":10,"title":"Later","container_extension":"mkv"}],"2":[{"id":"102","episode_num":1,"title":"Earlier","container_extension":"mp4"}]}}""")
            .series(connection, item)
        assertEquals(listOf(2, 10), series.episodes.map { it.season })
        val url = repository("{}").episodeStream(connection, series.episodes.first()).url.toHttpUrl()
        assertEquals("102.mp4", url.pathSegments.last())
        assertEquals("series", url.pathSegments[1])
        assertEquals(connection.password, url.pathSegments[3])
    }

    @Test fun `movie playback uses provider extension and encodes password as one path segment`() = runTest {
        val item = CatalogItem("81", "Sample movie", ContentType.MOVIE)
        val client = repository("""{"info":{"plot":"Description"},"movie_data":{"container_extension":"mkv"}}""")
        val details = client.movie(connection, item)
        val url = client.stream(connection, details.item).url.toHttpUrl()
        assertEquals("81.mkv", url.pathSegments.last())
        assertEquals(connection.password, url.pathSegments[3])
        assertEquals("Description", details.description)
    }

    @Test fun `missing stream extension is reported instead of guessed`() {
        val result = runCatching { repository("{}").episodeStream(connection, Episode("11", "Test", 1, 1, "")) }
        assertTrue(result.exceptionOrNull() is ProviderException)
    }

    @Test fun `empty object catalog is accepted and missing artwork does not hide a title`() = runTest {
        assertTrue(repository("{}").categories(connection, ContentType.LIVE).isEmpty())
        assertEquals(1, repository("""[{"stream_id":3,"name":"Channel","stream_icon":null}]""")
            .items(connection, ContentType.LIVE, "1").size)
    }

    @Test fun `HTTP transport parses short JSON bodies`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val result = HttpJsonTransport().load(server.url("/player_api.php"))
            assertEquals("[]", result.toString())
        }
    }

    @Test fun `transport errors never expose server values anywhere in the exception chain`() = runTest {
        for (response in listOf(
            MockResponse().setBody("<html>synthetic-secret</html>"),
            MockResponse().setResponseCode(403).setBody("synthetic-secret"),
            MockResponse().setResponseCode(302).addHeader("Location", "https://other.example.test?secret=synthetic-secret"),
        )) {
            MockWebServer().use { server ->
                server.enqueue(response)
                val error = runCatching { HttpJsonTransport().load(server.url("/api?password=synthetic-secret")) }.exceptionOrNull()
                assertTrue(error is ProviderException)
                assertFalse(error.toString().contains("synthetic-secret"))
                generateSequence(error) { it.cause }.forEach { cause -> assertFalse(cause.toString().contains("synthetic-secret")) }
                assertEquals(1, server.requestCount)
            }
        }
    }
}
