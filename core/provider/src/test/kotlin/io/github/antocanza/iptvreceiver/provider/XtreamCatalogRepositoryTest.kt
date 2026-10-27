package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.Episode
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    @Test fun `adult flags and category membership survive provider mapping`() = runTest {
        val categories = repository("""[{"category_id":"9","category_name":"Restricted","parent_id":0,"is_adult":true}]""")
            .categories(connection, ContentType.LIVE)
        assertTrue(categories.single().isAdult)
        val items = repository("""[{"stream_id":42,"name":"Channel","category_id":9,"is_adult":"1"}]""")
            .items(connection, ContentType.LIVE, null)
        assertEquals("9", items.single().categoryId)
        assertTrue(items.single().isAdult)
        val scoped = repository("""[{"stream_id":42,"name":"Channel"}]""").items(connection, ContentType.LIVE, "9")
        assertEquals("9", scoped.single().categoryId)
        assertFalse(scoped.single().isAdult)
    }
    private val connection = ProviderConnection("Test", "https://provider.example.test:8443/base/", "user+one", "test/&?+ value")
    private var requested: HttpUrl? = null
    private fun repository(response: String): XtreamCatalogRepository = XtreamCatalogRepository(JsonTransport { url ->
        requested = url
        Json.parseToJsonElement(response)
    })

    @Test fun `login accepts numeric or string fields and encodes credentials correctly`() = runTest {
        for (scheme in listOf("http", "https")) {
            val account = repository("""{"user_info":{"auth":"1","status":"Active","exp_date":null,"allowed_output_formats":["m3u8","ts","rtmp"]}}""")
                .authenticate(ProviderConnection("Test", " $scheme://provider.example.test:8443/base/ ", connection.username, connection.password))
            assertEquals(listOf("m3u8", "ts"), account.liveFormats)
            assertNull(account.expiresAtSeconds)
            assertEquals(scheme, requested?.scheme)
            assertEquals(8443, requested?.port)
            assertEquals("/base/player_api.php", requested?.encodedPath)
            assertEquals(connection.username, requested?.queryParameter("username"))
            assertEquals(connection.password, requested?.queryParameter("password"))
        }
    }

    @Test fun `login rejects invalid credentials inactive accounts and expired accounts`() = runTest {
        for ((response, reason) in listOf(
            """{"user_info":{"auth":0}}""" to FailureReason.AUTHENTICATION_FAILED,
            """{"user_info":{"auth":"0"}}""" to FailureReason.AUTHENTICATION_FAILED,
            """{"user_info":{"auth":1,"status":"Disabled"}}""" to FailureReason.INACTIVE_ACCOUNT,
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1"}}""" to FailureReason.EXPIRED_ACCOUNT,
        )) {
            val error = runCatching { repository(response).authenticate(connection) }.exceptionOrNull() as ProviderException
            assertEquals(reason, error.reason)
        }
    }

    @Test fun `HTTP authentication rejection is a credential failure without leaking the response`() = runTest {
        for (code in listOf(401, 403)) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(code).setBody("synthetic-secret"))
                val provider = ProviderConnection("Test", server.url("/").toString(), "demo", "synthetic-secret")
                val client = XtreamCatalogRepository()
                val error = runCatching { client.authenticate(provider) }.exceptionOrNull() as ProviderException
                assertEquals(FailureReason.AUTHENTICATION_FAILED, error.reason)
                assertFalse(error.toString().contains("synthetic-secret"))
                assertNull(error.cause)
                server.enqueue(MockResponse().setResponseCode(code))
                val catalogError = runCatching { client.categories(provider, ContentType.MOVIE) }.exceptionOrNull() as ProviderException
                assertEquals(FailureReason.ACCESS_DENIED, catalogError.reason)
            }
        }
    }

    @Test fun `invalid schemes and URLs containing credentials queries or fragments are rejected before transport`() = runTest {
        for (url in listOf("file:///private", "ftp://provider.example.test", "htpps://provider.example.test", "not a URL",
            "http://user:secret@provider.example.test", "https://user:secret@provider.example.test",
            "http://provider.example.test?password=secret", "https://provider.example.test?password=secret",
            "http://provider.example.test#fragment", "https://provider.example.test#fragment")) {
            requested = null
            val error = runCatching { repository("{}").authenticate(ProviderConnection("Test", url, "demo", "test")) }.exceptionOrNull()
            assertEquals(FailureReason.INVALID_SERVER, (error as ProviderException).reason)
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

    @Test fun `complete movie catalog omits the category filter and keeps titles across categories`() = runTest {
        val items = repository("""[{"stream_id":1,"name":"One","category_id":"7"},{"stream_id":2,"name":"Two","category_id":"8"},{"stream_id":1,"name":"One","category_id":"8"}]""")
            .items(connection, ContentType.MOVIE, null)
        assertEquals(listOf("1", "2"), items.map { it.id })
        assertEquals("get_vod_streams", requested?.queryParameter("action"))
        assertFalse(checkNotNull(requested).queryParameterNames.contains("category_id"))
    }

    @Test fun `movie subcategory requests retain their category filter`() = runTest {
        repository("[]").items(connection, ContentType.MOVIE, "7")
        assertEquals("get_vod_streams", requested?.queryParameter("action"))
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

    @Test fun `live movie and episode playback preserve HTTP and HTTPS provider addresses`() {
        val client = repository("{}")
        for (scheme in listOf("http", "https")) {
            val provider = ProviderConnection("Test", "$scheme://provider.example.test:8080/base/", connection.username, connection.password)
            val sources = listOf(
                client.stream(provider, CatalogItem("81", "Live", ContentType.LIVE)),
                client.stream(provider, CatalogItem("82", "Movie", ContentType.MOVIE, extension = "mkv")),
                client.episodeStream(provider, Episode("11", "Episode", 1, 1, "mp4")),
            )
            assertEquals(listOf("/base/live/", "/base/movie/", "/base/series/"), sources.map {
                val url = it.url.toHttpUrl()
                assertEquals(scheme, url.scheme)
                assertEquals(8080, url.port)
                assertEquals(connection.password, url.pathSegments[3])
                "/${url.pathSegments.take(2).joinToString("/")}/"
            })
        }
    }

    @Test fun `HTTP provider authenticates and loads its catalog through the real transport`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active"}}"""))
            server.enqueue(MockResponse().setBody("""[{"category_id":"7","category_name":"Movies"}]"""))
            server.enqueue(MockResponse().setBody("""[{"stream_id":"81","name":"Sample","container_extension":"mp4"}]"""))
            val provider = ProviderConnection("Test", server.url("/base/").toString(), connection.username, connection.password)
            val client = XtreamCatalogRepository()
            assertEquals("Active", client.authenticate(provider).status)
            assertEquals("7", client.categories(provider, ContentType.MOVIE).single().id)
            assertEquals("81", client.items(provider, ContentType.MOVIE, "7").single().id)
            for (action in listOf(null, "get_vod_categories", "get_vod_streams")) {
                val url = checkNotNull(server.takeRequest().requestUrl)
                assertEquals("/base/player_api.php", url.encodedPath)
                assertEquals(connection.username, url.queryParameter("username"))
                assertEquals(connection.password, url.queryParameter("password"))
                assertEquals(action, url.queryParameter("action"))
            }
        }
    }

    @Test fun `empty object catalog is accepted and missing artwork does not hide a title`() = runTest {
        assertTrue(repository("{}").categories(connection, ContentType.LIVE).isEmpty())
        assertEquals(1, repository("""[{"stream_id":3,"name":"Channel","stream_icon":null}]""")
            .items(connection, ContentType.LIVE, "1").size)
    }

    @Test fun `all series request omits category and maps series IDs across categories`() = runTest {
        val result = repository("""[{"series_id":91,"name":"Star Wars One","category_id":"1"},{"series_id":92,"name":"Star Wars Two","category_id":"2"}]""")
            .items(connection, ContentType.SERIES, null)
        assertEquals(listOf("91", "92"), result.map { it.id })
        assertTrue(result.all { it.type == ContentType.SERIES })
        assertEquals("get_series", requested?.queryParameter("action"))
        assertNull(requested?.queryParameter("category_id"))
    }

    @Test fun `HTTP transport parses short JSON bodies`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val result = HttpJsonTransport().load(server.url("/player_api.php"))
            assertEquals("[]", result.toString())
        }
    }

    @Test fun `streamed full catalogs can exceed the ordinary JSON limit`() = runTest {
        val payload = buildString {
            append('[')
            val ignoredMetadata = "x".repeat(1024)
            repeat(21_000) { index ->
                if (index > 0) append(',')
                append("""{"stream_id":$index,"unused":"$ignoredMetadata"}""")
            }
            append(']')
        }
        assertTrue(payload.length > 20 * 1024 * 1024)
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(payload))
            server.enqueue(MockResponse().setBody(payload))
            val transport = HttpJsonTransport()
            val items = transport.loadCatalog(server.url("/api?action=get_vod_streams")) { element ->
                CatalogItem((element as JsonObject)["stream_id"].toString(), "Sample", ContentType.MOVIE)
            }
            assertEquals(21_000, items.size)
            assertEquals("20999", items.last().id)
            val ordinaryLoad = runCatching { transport.load(server.url("/api")) }.exceptionOrNull()
            assertEquals(FailureReason.TOO_LARGE, (ordinaryLoad as ProviderException).reason)
        }
    }

    @Test fun `streamed catalogs preserve empty array and empty object compatibility`() = runTest {
        for (body in listOf("[]", " {} ", " \n\t[]")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                val items = HttpJsonTransport().loadCatalog(server.url("/api")) { error("No items expected") }
                assertTrue(items.isEmpty())
            }
        }
    }

    @Test fun `streamed catalog byte limit is enforced without exposing private data`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("""[{"stream_id":1,"unused":"synthetic-secret-${"x".repeat(128)}"}]""", 8))
            val error = runCatching {
                HttpJsonTransport(fullCatalogLimitBytes = 64).loadCatalog(server.url("/api?password=synthetic-secret")) { null }
            }.exceptionOrNull()
            assertEquals(FailureReason.TOO_LARGE, (error as ProviderException).reason)
            assertFalse(error.toString().contains("synthetic-secret"))
            assertNull(error.cause)
        }
    }

    @Test fun `malformed streamed catalogs fail safely instead of returning partial results`() = runTest {
        for ((index, body) in listOf("""[{"stream_id":1},synthetic-secret]""", """[{"stream_id":1},""", """{"error":"synthetic-secret"}""", "[] []", "[] synthetic-secret").withIndex()) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                val error = runCatching {
                    HttpJsonTransport().loadCatalog(server.url("/api")) { CatalogItem("1", "Sample", ContentType.MOVIE) }
                }.exceptionOrNull()
                assertTrue("Malformed catalog case $index must fail safely", error is ProviderException)
                assertEquals(FailureReason.UNSUPPORTED_RESPONSE, (error as ProviderException).reason)
                assertFalse(error.toString().contains("synthetic-secret"))
                assertNull(error.cause)
            }
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
