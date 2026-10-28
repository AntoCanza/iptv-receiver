package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.playback.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `resume seeks once before preparation and does not overwrite an unwatched point`() = runTest(dispatcher) {
        val engine = ProgressEngine()
        val points = mutableListOf<PlaybackProgress>()
        val store = ViewModelStore()
        try {
            val factory = viewModelFactory { initializer { PlaybackViewModel(engine, false, 900_000, points::add) } }
            val model = ViewModelProvider.create(store, factory)[PlaybackViewModel::class]
            assertEquals(listOf("seek:900000", "prepare"), engine.actions)
            model.onHostStopped(false)
            assertTrue(points.isEmpty())
            assertSame(model, ViewModelProvider.create(store, factory)[PlaybackViewModel::class])
            assertEquals(1, engine.actions.count { it.startsWith("seek:") })
            store.clear()
            assertTrue(points.isEmpty())
        } finally { store.clear() }
    }

    @Test fun `playback checkpoints every five seconds and records seek pause background and final exit`() = runTest(dispatcher) {
        val engine = ProgressEngine()
        val points = mutableListOf<PlaybackProgress>()
        val store = ViewModelStore()
        try {
            val model = ViewModelProvider.create(store, viewModelFactory { initializer { PlaybackViewModel(engine, false, 900_000, points::add) } })[PlaybackViewModel::class]
            engine.playing(true); runCurrent()
            assertEquals(900_000L, points.last().positionMs)
            engine.position = 905_000
            advanceTimeBy(5_000); runCurrent()
            assertEquals(905_000L, points.last().positionMs)
            engine.position = 600_000; engine.playing(false)
            assertEquals(600_000L, points.last().positionMs)
            engine.position = 600_100; engine.playing(false)
            assertEquals(600_100L, points.last().positionMs)
            engine.position = 610_000; engine.playing(true); runCurrent()
            model.onHostStopped(true)
            assertFalse(engine.actions.contains("pause"))
            engine.position = 610_200; model.onHostStopped(false)
            assertEquals(610_200L, points.last().positionMs)
            assertTrue(engine.actions.contains("pause"))
            engine.position = 620_000; store.clear()
            assertEquals(620_000L, points.last().positionMs)
            assertTrue(engine.listeners.isEmpty())
            assertEquals("release", engine.actions.last())
        } finally { store.clear() }
    }

    @Test fun `end marks the title completed and unknown duration can still resume`() = runTest(dispatcher) {
        val engine = ProgressEngine()
        val points = mutableListOf<PlaybackProgress>()
        val store = ViewModelStore()
        try {
            ViewModelProvider.create(store, viewModelFactory { initializer { PlaybackViewModel(engine, false, onProgress = points::add) } })[PlaybackViewModel::class]
            engine.duration = -9223372036854775807; engine.position = 900_000; engine.playing(true); runCurrent()
            assertNull(points.last().durationMs)
            assertTrue(points.last().canResume)
            engine.state = Player.STATE_ENDED; engine.playing(false)
            assertTrue(points.last().completed)
            assertFalse(points.last().canResume)
            store.clear()
        } finally { store.clear() }
    }

    private class ProgressEngine : PlaybackEngine {
        val listeners = mutableListOf<Player.Listener>()
        val actions = mutableListOf<String>()
        var position = 0L
        var duration = 6_600_000L
        var state = Player.STATE_READY
        override val recoveryState = MutableStateFlow(PlaybackRecoveryState.NONE)
        override val keepScreenOn = MutableStateFlow(false)
        override val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> duration
                "getPlaybackState" -> state
                "seekTo" -> { position = args!![0] as Long; actions += "seek:$position"; null }
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                else -> null
            }
        } as Player
        fun playing(value: Boolean) { listeners.toList().forEach { it.onIsPlayingChanged(value) } }
        override fun prepareAndPlay() { actions += "prepare" }
        override fun pause() { actions += "pause"; playing(false) }
        override fun release() { actions += "release" }
    }
}
