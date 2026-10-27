package io.github.antocanza.iptvreceiver.ui

import io.github.antocanza.iptvreceiver.playback.FirstPlaybackListener
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstPlaybackListenerTest {
    @Test fun `failed or buffering streams are not watched and pause retry or rotation cannot duplicate a watch`() {
        var watches = 0
        val listener = FirstPlaybackListener { watches++ }
        listener.onIsPlayingChanged(false)
        listener.onIsPlayingChanged(false)
        assertEquals(0, watches)
        listener.onIsPlayingChanged(true)
        assertEquals(1, watches)
        listener.onIsPlayingChanged(false)
        listener.onIsPlayingChanged(true)
        listener.onIsPlayingChanged(true)
        assertEquals(1, watches)
        FirstPlaybackListener { watches++ }.onIsPlayingChanged(true)
        assertEquals(2, watches)
    }
}
