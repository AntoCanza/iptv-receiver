package io.github.antocanza.iptvreceiver.provider

import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlsTest {
    private fun connection(allowHttp: Boolean = false) =
        ProviderConnection("Test", "https://provider.example.test/base", "demo", "synthetic-secret", allowHttp)
    private fun urls(json: String, allowHttp: Boolean = false) =
        artworkUrls(Json.parseToJsonElement(json).jsonObject, connection(allowHttp))

    @Test fun `HTTP posters try HTTPS first and retain HTTP fallback only for an opted in connection`() {
        val json = """{"cover":"http://images.example.test:80/poster.jpg"}"""
        assertEquals(listOf("https://images.example.test/poster.jpg"), urls(json))
        assertEquals(listOf("https://images.example.test/poster.jpg", "http://images.example.test/poster.jpg"), urls(json, true))
    }

    @Test fun `invalid primary artwork does not hide a valid cover and alternates are preserved`() {
        val urls = urls("""{"stream_icon":"N/A","cover":"https://images.example.test/cover.jpg","movie_image":"https://images.example.test/alternate.jpg","poster":"https://images.example.test/cover.jpg"}""")
        assertEquals(listOf("https://images.example.test/cover.jpg", "https://images.example.test/alternate.jpg"), urls)
    }

    @Test fun `relative and protocol relative posters resolve without adding account parameters`() {
        val urls = urls("""{"cover":"/images/poster.jpg","poster":"//images.example.test/second.png"}""")
        assertEquals(listOf("https://provider.example.test/images/poster.jpg", "https://images.example.test/second.png"), urls)
        assertFalse(urls.joinToString().contains("synthetic-secret"))
    }

    @Test fun `unsafe schemes embedded credentials and empty sentinels are rejected`() {
        for (value in listOf("file:///private/poster.jpg", "content://private/picture", "https://user:secret@images.example.test/poster.jpg", "null", "none", "0", "")) {
            assertTrue(urls("""{"cover":"$value"}""", true).isEmpty())
        }
    }

    @Test fun `movie detail artwork repairs a missing list image`() = runTest {
        val repository = XtreamCatalogRepository(JsonTransport {
            Json.parseToJsonElement("""{"info":{"movie_image":"https://images.example.test/detail.jpg"},"movie_data":{"container_extension":"mp4"}}""")
        })
        val result = repository.movie(connection(), CatalogItem("51", "Sample", ContentType.MOVIE))
        assertEquals("https://images.example.test/detail.jpg", result.item.imageUrl)
    }
}
