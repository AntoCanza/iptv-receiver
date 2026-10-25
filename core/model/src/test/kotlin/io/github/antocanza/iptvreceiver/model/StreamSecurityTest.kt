package io.github.antocanza.iptvreceiver.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSecurityTest {
    @Test fun `unencrypted manifests and segments require account opt in`() {
        assertFalse(ProviderConnection("Test", "https://provider.example.test", "demo", "test").allowHttpStreams)
        assertTrue(runCatching { requireAllowedStreamScheme("https", false) }.isSuccess)
        assertEquals(FailureReason.INSECURE_STREAM,
            (runCatching { requireAllowedStreamScheme("http", false) }.exceptionOrNull() as ProviderException).reason)
        assertTrue(runCatching { requireAllowedStreamScheme("http", true) }.isSuccess)
    }

    @Test fun `opting into HTTP never permits local files or other URI schemes`() {
        for (scheme in listOf("file", "content", "ftp", null)) {
            for (allowHttp in listOf(false, true)) {
                assertEquals(FailureReason.INVALID_STREAM,
                    (runCatching { requireAllowedStreamScheme(scheme, allowHttp) }.exceptionOrNull() as ProviderException).reason)
            }
        }
    }
}
