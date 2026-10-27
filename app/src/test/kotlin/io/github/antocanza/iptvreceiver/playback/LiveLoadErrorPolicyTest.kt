package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.C
import io.github.antocanza.iptvreceiver.data.InvalidStreamSchemeException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class LiveLoadErrorPolicyTest {
    private val policy = LiveLoadErrorPolicy()

    @Test fun `temporary network failures retain the buffer with short bounded retry delays`() {
        assertEquals(0L, policy.retryDelayFor(IOException(), 1, 0))
        assertEquals(1_000L, policy.retryDelayFor(IOException(), 2, 1_000))
        assertEquals(1_500L, policy.retryDelayFor(IOException(), 8, 5_000))
    }

    @Test fun `persistent loader failures stop after ten attempts`() {
        assertEquals(C.TIME_UNSET, policy.retryDelayFor(IOException(), 10, 5_000))
        assertEquals(C.TIME_UNSET, policy.retryDelayFor(IOException(), 20, 5_000))
    }

    @Test fun `invalid streams and TLS validation failures do not enter the longer retry loop`() {
        assertEquals(C.TIME_UNSET, policy.retryDelayFor(InvalidStreamSchemeException(), 1, 0))
        assertEquals(C.TIME_UNSET, policy.retryDelayFor(IOException(SSLHandshakeException("Synthetic TLS failure")), 1, 0))
    }

    @Test fun `Media3 non retryable failures retain their terminal decision`() {
        assertEquals(C.TIME_UNSET, policy.retryDelayFor(IOException(), 1, C.TIME_UNSET))
    }
}
