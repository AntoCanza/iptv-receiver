package io.github.antocanza.iptvreceiver.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ArtworkHttpClientTest {
    @Test fun `secure artwork loader never sends an HTTP request`() {
        MockWebServer().use { server ->
            val result = runCatching {
                artworkHttpClient(false).newCall(Request.Builder().url(server.url("/poster.jpg")).build()).execute().close()
            }
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(0, server.requestCount)
            assertFalse(result.exceptionOrNull().toString().contains("poster.jpg"))
        }
    }

    @Test fun `compatible artwork loader can follow an HTTP redirect and download the image`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/image.jpg"))
            server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody("synthetic-image-data"))
            artworkHttpClient(true).newCall(Request.Builder().url(server.url("/poster.jpg")).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("synthetic-image-data", response.body.string())
            }
            assertEquals(2, server.requestCount)
        }
    }
}
