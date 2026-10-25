package io.github.antocanza.iptvreceiver.model

import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactionTest {
    @Test fun `connection and playback source redact their string representation`() {
        val connection = ProviderConnection("Private name", "https://provider.example.test", "sample-user", "synthetic-secret")
        val source = PlaybackSource("https://provider.example.test/live/sample-user/synthetic-secret/1.ts", "Channel", true)
        for (value in listOf(connection.toString(), source.toString())) {
            assertFalse(value.contains("synthetic-secret"))
            assertFalse(value.contains("sample-user"))
            assertFalse(value.contains("provider.example.test"))
        }
    }
}
