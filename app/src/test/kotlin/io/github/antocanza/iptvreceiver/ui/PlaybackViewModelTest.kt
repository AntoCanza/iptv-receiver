package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import io.github.antocanza.iptvreceiver.playback.PlaybackEngine
import io.github.antocanza.iptvreceiver.playback.PlaybackRecoveryState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackViewModelTest {
    @Test fun `recreated hosts retain one player at the existing position and release only when leaving`() {
        val store = ViewModelStore()
        val engine = FakeEngine()
        var creations = 0
        val factory = viewModelFactory { initializer { creations++; PlaybackViewModel(engine, false) } }
        val portrait = ViewModelProvider.create(store, factory)[PlaybackViewModel::class]
        engine.positionMs = 185_000L
        portrait.onHostStopped(changingConfigurations = true)
        val landscape = ViewModelProvider.create(store, factory)[PlaybackViewModel::class]
        landscape.onOrientationChanged(true)
        val portraitAgain = ViewModelProvider.create(store, factory)[PlaybackViewModel::class]
        portraitAgain.onOrientationChanged(false)
        assertSame(portrait, landscape)
        assertSame(portrait, portraitAgain)
        assertEquals(1, creations)
        assertEquals(1, engine.prepareCalls)
        assertEquals(185_000L, engine.positionMs)
        assertTrue(engine.playing)
        assertEquals(0, engine.releaseCalls)
        store.clear()
        assertEquals(1, engine.releaseCalls)
    }

    @Test fun `rotation preserves a user's paused state and backgrounding pauses playback`() {
        val engine = FakeEngine()
        val model = PlaybackViewModel(engine, false)
        engine.pause()
        engine.positionMs = 98_000L
        model.onHostStopped(changingConfigurations = true)
        model.onOrientationChanged(true)
        assertFalse(engine.playing)
        assertEquals(98_000L, engine.positionMs)
        assertEquals(1, engine.prepareCalls)
        engine.playing = true
        model.onHostStopped(changingConfigurations = false)
        assertFalse(engine.playing)
    }

    @Test fun `fullscreen and orientation changes never prepare or rewind media`() {
        val engine = FakeEngine()
        val model = PlaybackViewModel(engine, false)
        engine.positionMs = 75_000L
        model.setFullscreen(true)
        assertTrue(model.fullscreen.value)
        model.setFullscreen(false)
        model.onOrientationChanged(true)
        assertTrue(model.fullscreen.value)
        model.setFullscreen(false)
        assertFalse(model.fullscreen.value)
        model.onOrientationChanged(false)
        assertFalse(model.fullscreen.value)
        assertEquals(75_000L, engine.positionMs)
        assertEquals(1, engine.prepareCalls)
    }

    private class FakeEngine : PlaybackEngine {
        override val player: Player get() = error("UI-only player access is not needed by the retained session test")
        override val recoveryState = MutableStateFlow(PlaybackRecoveryState.NONE)
        var prepareCalls = 0
        var releaseCalls = 0
        var positionMs = 0L
        var playing = false
        override fun prepareAndPlay() { prepareCalls++; positionMs = 0; playing = true }
        override fun pause() { playing = false }
        override fun release() { releaseCalls++; playing = false }
    }
}
