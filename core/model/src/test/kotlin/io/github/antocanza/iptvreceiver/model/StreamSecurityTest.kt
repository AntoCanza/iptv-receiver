package io.github.antocanza.iptvreceiver.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSecurityTest {
    @Test fun `HTTP and HTTPS manifests and segments are always accepted`() {
        for (scheme in listOf("http", "https", "HTTP", "HTTPS")) {
            assertTrue(runCatching { requireAllowedStreamScheme(scheme) }.isSuccess)
        }
    }

    @Test fun `local files and other URI schemes are rejected`() {
        for (scheme in listOf("file", "content", "ftp", "data", "", null)) {
            assertEquals(FailureReason.INVALID_STREAM,
                (runCatching { requireAllowedStreamScheme(scheme) }.exceptionOrNull() as ProviderException).reason)
        }
    }
}
