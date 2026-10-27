package io.github.antocanza.iptvreceiver.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkHttpClientTest {
    @Test fun `artwork loader accepts HTTP without a connection option`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("synthetic-image-data"))
            val client = artworkHttpClient()
            assertTrue(client.followSslRedirects)
            client.newCall(Request.Builder().url(server.url("/poster.jpg")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("synthetic-image-data", response.body.string())
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `artwork loader can follow an HTTP redirect and download the image`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/image.jpg"))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("synthetic-image-data"))
            artworkHttpClient().newCall(Request.Builder().url(server.url("/poster.jpg")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("synthetic-image-data", response.body.string())
            }
            assertEquals(2, server.requestCount)
        }
    }
}
