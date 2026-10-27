package io.github.antocanza.iptvreceiver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveManifestSessionTest {
    private val origin = "https://provider.example.test/live/demo/synthetic/42.m3u8"
    private val first = "https://delivery.example.test/session-one/live.m3u8"
    private val fresh = "https://delivery.example.test/session-two/live.m3u8"

    @Test fun `single media playlists follow refreshed session URLs instead of stale redirects`() {
        val session = LiveManifestSession(origin)
        assertEquals(origin, session.resolve(origin))
        session.parsed(first, isMediaPlaylist = true)
        assertEquals(first, session.resolve(first))
        assertTrue(session.canRefresh(first))
        session.refreshed(fresh)
        assertEquals(fresh, session.resolve(first))
        session.parsed(first, isMediaPlaylist = true)
        assertEquals(fresh, session.resolve(first))
    }

    @Test fun `master playlists keep independent variant requests unchanged`() {
        val session = LiveManifestSession(origin)
        session.parsed(first, isMediaPlaylist = false)
        session.parsed(fresh, isMediaPlaylist = true)
        session.refreshed(fresh)
        assertEquals(first, session.resolve(first))
        assertEquals(fresh, session.resolve(fresh))
        assertFalse(session.canRefresh(first))
    }

    @Test fun `the original authenticated request cannot enter a refresh loop`() {
        val session = LiveManifestSession(origin)
        assertFalse(session.canRefresh(first))
        session.parsed(first, isMediaPlaylist = true)
        assertFalse(session.canRefresh(origin))
    }

    @Test fun `diagnostics never expose credential or redirected session URLs`() {
        val session = LiveManifestSession(origin)
        session.parsed(first, isMediaPlaylist = true)
        assertEquals("LiveManifestSession([redacted])", session.toString())
    }
}
