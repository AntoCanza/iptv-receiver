package io.github.antocanza.iptvreceiver.ui

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class SettingsPresentationTest {
    @Test fun `server clock is displayed in the reported timezone rather than silently using the phone zone`() {
        val timestamp = Instant.parse("2026-10-25T12:00:00Z").epochSecond
        val tokyo = formatProviderTime(timestamp, Locale.UK, "Asia/Tokyo", includeTime = true)!!
        val utc = formatProviderTime(timestamp, Locale.UK, "UTC", includeTime = true)!!
        assertTrue(tokyo.contains("21:00"))
        assertTrue(utc.contains("12:00"))
    }

    @Test fun `missing or corrupt dates stay absent rather than crashing account and server settings`() {
        assertNull(formatProviderTime(null, Locale.US))
        assertNull(formatProviderTime(Long.MAX_VALUE, Locale.US))
        assertNull(formatProviderTime(1792929600L, Locale.US, "not-a-zone"))
    }
}
