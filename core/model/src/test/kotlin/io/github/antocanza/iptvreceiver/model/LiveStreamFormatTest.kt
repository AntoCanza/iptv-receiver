package io.github.antocanza.iptvreceiver.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStreamFormatTest {
    @Test fun `live channels prefer HLS even when the provider lists TS first`() {
        assertEquals("m3u8", Account("Active", null, listOf("ts", "m3u8")).preferredLiveFormat)
        assertEquals("m3u8", Account("Active", null, listOf("m3u8", "ts")).preferredLiveFormat)
    }

    @Test fun `providers without HLS retain TS and legacy fallback behavior`() {
        assertEquals("ts", Account("Active", null, listOf("ts")).preferredLiveFormat)
        assertEquals("ts", Account("Active", null, emptyList()).preferredLiveFormat)
        assertEquals("mp4", Account("Active", null, listOf("mp4")).preferredLiveFormat)
    }

    @Test fun `format preferences normalize case and ignore blank advertised values`() {
        assertEquals("m3u8", Account("Active", null, listOf("", " TS ", "M3U8")).preferredLiveFormat)
        assertEquals("ts", Account("Active", null, listOf(" ")).preferredLiveFormat)
    }
}
