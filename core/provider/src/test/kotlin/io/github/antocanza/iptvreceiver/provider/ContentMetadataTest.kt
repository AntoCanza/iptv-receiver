package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentMetadata
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ServerInfo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentMetadataTest {
    private val connection = ProviderConnection("Test", "https://provider.example.test/base", "demo", "synthetic-secret")
    private fun metadata(json: String, fallback: ContentMetadata = ContentMetadata()) =
        contentMetadata(Json.parseToJsonElement(json).jsonObject, connection, fallback)
    private fun repository(json: String) = XtreamCatalogRepository(JsonTransport { Json.parseToJsonElement(json) })

    @Test fun `listing metadata accepts provider field variants without losing titles`() = runTest {
        val result = repository("""[{"stream_id":51,"name":"A movie","year":"2024","rating_5based":"4.25","genre":"Drama, Adventure / drama","added":"1700000000"}]""")
            .items(connection, ContentType.MOVIE, null).single()
        assertEquals(2024, result.metadata.year)
        assertEquals(8.5, result.metadata.rating!!, 0.001)
        assertEquals(listOf("Drama", "Adventure"), result.metadata.genres)
        assertEquals(1700000000L, result.addedAtSeconds)
    }

    @Test fun `absent malformed and sentinel fields remain absent rather than creating false badges`() {
        assertEquals(ContentMetadata(), metadata("""{"year":"unknown","rating":"NaN","rating_5based":99,"genre":[null,"null","0","N/A"],"cast":null,"director":"-","country":"none","duration_secs":"9223372036854775807","duration":"00:99:01","runtime":"-1","youtube_trailer":"javascript:alert(1)","backdrop_path":["file:///private/image.png"]}"""))
        assertEquals(ContentMetadata(), metadata("""{"year":0,"rating":0,"genre":{},"duration":{},"duration_secs":0}"""))
        assertNull(metadata("""{"rating":"Infinity"}""").rating)
    }

    @Test fun `detail fields enrich listing metadata and missing fields preserve useful listing values`() = runTest {
        val fallback = ContentMetadata(year = 2023, rating = 7.2, genres = listOf("Drama"))
        val item = CatalogItem("51", "List title", ContentType.MOVIE, metadata = fallback)
        val details = repository("""{"info":{"name":"Detail title","cast":"A Person, B Person","director":"A Director","country":"Italy","duration":"01:42:30","youtube_trailer":"abcdefghijk","backdrop_path":["/images/background.jpg","file:///private/bad.png"]},"movie_data":{"container_extension":"mkv"}}""").movie(connection, item)
        assertEquals("Detail title", details.item.title)
        assertEquals(2023, details.item.metadata.year)
        assertEquals(7.2, details.item.metadata.rating!!, 0.001)
        assertEquals(listOf("Drama"), details.item.metadata.genres)
        assertEquals(6150L, details.item.metadata.durationSeconds)
        assertEquals("A Person, B Person", details.item.metadata.cast)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", details.item.metadata.trailerUrl)
        assertEquals(listOf("https://provider.example.test/images/background.jpg"), details.item.metadata.backdropUrls)
        assertFalse(details.item.metadata.backdropUrls.joinToString().contains(connection.password))
    }

    @Test fun `ratings use ten point scale and valid detail values replace listing values`() {
        val result = metadata("""{"releaseDate":"2025-02-12","rating":"8.3","rating_5based":1,"genre":["Thriller","Science Fiction"],"duration_secs":"5400"}""")
        assertEquals(2025, result.year)
        assertEquals(8.3, result.rating!!, 0.001)
        assertEquals(5400L, result.durationSeconds)
        assertEquals(listOf("Thriller", "Science Fiction"), result.genres)
        assertEquals(48 * 60L, metadata("""{"episode_run_time":"48"}""").durationSeconds)
    }

    @Test fun `trailer links accept only web URLs without embedded credentials`() {
        for (value in listOf("file:///private/a.mp4", "intent://trailer", "https://user:secret@example.test/a", "https://example.test@other.test:bad/a")) {
            assertNull(metadata("""{"youtube_trailer":"$value"}""").trailerUrl)
        }
        assertEquals("https://youtu.be/abcdefghijk", metadata("""{"youtube_trailer":"https://youtu.be/abcdefghijk"}""").trailerUrl)
    }

    @Test fun `series details retain seasons episode summaries thumbnails runtimes and detail artwork`() = runTest {
        val item = CatalogItem("81", "List series", ContentType.SERIES, imageUrl = "https://images.example.test/list.jpg")
        val details = repository("""{"info":{"name":"Full series","plot":"Series overview","cover":"/images/series.jpg","genre":"Drama","rating":"8"},"seasons":[{"season_number":0,"name":"Specials"},{"season_number":"1","name":"First season","overview":"A new beginning"},{"season_number":"bad"}],"episodes":{"1":[{"id":12,"title":"Second","season":1,"episode_num":2,"container_extension":"mp4","info":{"plot":"Episode overview","duration_secs":"2820","movie_image":"/images/episode.jpg"}},{"id":11,"title":"First","season":1,"episode_num":1,"container_extension":"mkv"}],"0":[{"id":10,"title":"Special","season":0,"episode_num":1,"container_extension":"mp4"}]}}""").series(connection, item)
        assertEquals("Full series", details.title)
        assertEquals(listOf(0, 1), details.seasons.map { it.number })
        assertEquals("A new beginning", details.seasons.last().description)
        assertEquals(listOf("10", "11", "12"), details.episodes.map { it.id })
        assertEquals("Episode overview", details.episodes.last().description)
        assertEquals(2820L, details.episodes.last().durationSeconds)
        assertEquals(listOf("https://provider.example.test/images/episode.jpg"), details.episodes.last().imageUrls)
        assertEquals("https://provider.example.test/images/series.jpg", details.imageUrls.first())
        assertTrue(details.imageUrls.contains(item.imageUrl))
        assertEquals(listOf("Drama"), details.metadata.genres)
    }

    @Test fun `account connection counts and trial status remain optional and are not fabricated`() = runTest {
        val account = repository("""{"user_info":{"auth":1,"status":"Active","active_cons":"0","max_connections":2,"is_trial":"1"}}""").authenticate(connection)
        assertEquals(0, account.activeConnections)
        assertEquals(2, account.maxConnections)
        assertEquals(true, account.isTrial)
        val missing = repository("""{"user_info":{"auth":1,"status":"Active","active_cons":"-1","max_connections":"bad","is_trial":"unknown"}}""").authenticate(connection)
        assertNull(missing.activeConnections)
        assertNull(missing.maxConnections)
        assertNull(missing.isTrial)
    }

    @Test fun `server settings retain only optional valid timezone and sign in clock metadata`() = runTest {
        val account = repository("""{"user_info":{"auth":1,"status":"Active"},"server_info":{"timezone":"Europe/Rome","timestamp_now":"1792929600","password":"synthetic-server-secret","url":"private.internal"}}""").authenticate(connection)
        assertEquals(ServerInfo("Europe/Rome", 1792929600L), account.serverInfo)
        assertFalse(account.serverInfo.toString().contains("synthetic-server-secret"))
        assertFalse(account.serverInfo.toString().contains("private.internal"))
        val malformed = repository("""{"user_info":{"auth":1,"status":"Active"},"server_info":{"timezone":"bad-zone","timestamp_now":"-1"}}""").authenticate(connection)
        assertEquals(ServerInfo(), malformed.serverInfo)
        val missing = repository("""{"user_info":{"auth":1,"status":"Active"},"server_info":[]}""").authenticate(connection)
        assertEquals(ServerInfo(), missing.serverInfo)
    }
}
