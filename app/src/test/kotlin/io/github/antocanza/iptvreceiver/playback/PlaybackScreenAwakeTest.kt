package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackScreenAwakeTest {
    @Test fun `active playback stays awake without user interaction`() = runTest {
        val session = session()
        session.player.change(Player.STATE_READY, play = true)
        advance(3_600_000)
        assertTrue(session.awake.keepScreenOn.value)
    }

    @Test fun `pause end stop and permanent failure allow normal screen timeout`() = runTest {
        for (state in listOf(Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE)) {
            val session = session()
            session.player.change(Player.STATE_READY, play = true)
            assertTrue(session.awake.keepScreenOn.value)
            session.player.change(state, play = state != Player.STATE_READY)
            assertFalse(session.awake.keepScreenOn.value)
            session.awake.release()
        }
        val session = session()
        session.player.change(Player.STATE_BUFFERING, play = true)
        session.recovery.value = PlaybackRecoveryState.FAILED
        runCurrent()
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `startup and brief buffering stay awake but continuous waiting expires after one minute`() = runTest {
        val session = session()
        assertFalse(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(59_999)
        assertTrue(session.awake.keepScreenOn.value)
        advance(1)
        assertFalse(session.awake.keepScreenOn.value)
        advance(120_000)
        assertFalse(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_READY, play = true)
        assertTrue(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(30_000)
        assertTrue(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_READY, play = true)
        advance(60_000)
        assertTrue(session.awake.keepScreenOn.value)
    }

    @Test fun `live reconnect transitions share a deadline instead of renewing it on each attempt`() = runTest {
        val session = session()
        session.player.change(Player.STATE_READY, play = true)
        session.recovery.value = PlaybackRecoveryState.RECONNECTING
        session.player.change(Player.STATE_ENDED, play = true)
        advance(20_000)
        session.player.change(Player.STATE_IDLE, play = true)
        advance(20_000)
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(19_999)
        assertTrue(session.awake.keepScreenOn.value)
        advance(1)
        assertFalse(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_IDLE, play = true)
        advance(5_000)
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `exhausted retries release the hold immediately even before grace expires`() = runTest {
        val session = session()
        session.recovery.value = PlaybackRecoveryState.RECONNECTING
        session.player.change(Player.STATE_IDLE, play = true)
        runCurrent()
        assertTrue(session.awake.keepScreenOn.value)
        session.recovery.value = PlaybackRecoveryState.FAILED
        runCurrent()
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `manual retry renews expired grace even when buffering state does not change`() = runTest {
        val session = session()
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(60_000)
        assertFalse(session.awake.keepScreenOn.value)
        session.awake.onPlaybackRequested()
        assertTrue(session.awake.keepScreenOn.value)
        advance(59_999)
        assertTrue(session.awake.keepScreenOn.value)
        advance(1)
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `pausing buffering cancels grace and resume gets a new bounded wait`() = runTest {
        val session = session()
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(45_000)
        session.player.change(Player.STATE_BUFFERING, play = false)
        assertFalse(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_BUFFERING, play = true)
        advance(30_000)
        assertTrue(session.awake.keepScreenOn.value)
        advance(30_000)
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `suppressed playback allows sleep and recovering audio focus restores the hold`() = runTest {
        val session = session()
        session.player.change(Player.STATE_READY, play = true)
        session.player.suppress(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        assertFalse(session.awake.keepScreenOn.value)
        session.player.suppress(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        assertTrue(session.awake.keepScreenOn.value)
        session.player.change(Player.STATE_BUFFERING, play = true)
        session.player.suppress(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        advance(60_000)
        assertFalse(session.awake.keepScreenOn.value)
    }

    @Test fun `leaving removes listeners cancels timers and cannot restore a stale hold`() = runTest {
        val session = session()
        session.player.change(Player.STATE_BUFFERING, play = true)
        session.awake.release()
        assertTrue(session.player.listeners.isEmpty())
        session.recovery.value = PlaybackRecoveryState.RECONNECTING
        session.player.change(Player.STATE_READY, play = true)
        advance(60_000)
        assertFalse(session.awake.keepScreenOn.value)
    }

    private fun TestScope.advance(ms: Long) { runCurrent(); advanceTimeBy(ms); runCurrent() }

    private fun TestScope.session(): Session {
        val player = FakePlayer()
        val recovery = MutableStateFlow(PlaybackRecoveryState.NONE)
        return Session(player, recovery, PlaybackScreenAwake(player.proxy, recovery, backgroundScope))
    }

    private data class Session(val player: FakePlayer, val recovery: MutableStateFlow<PlaybackRecoveryState>, val awake: PlaybackScreenAwake)

    private class FakePlayer {
        val listeners = mutableListOf<Player.Listener>()
        private var state = Player.STATE_IDLE
        private var playWhenReady = false
        private var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        private val isPlaying get() = state == Player.STATE_READY && playWhenReady && suppression == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        val proxy = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "getPlaybackState" -> state
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackSuppressionReason" -> suppression
                "isPlaying" -> isPlaying
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun change(value: Int, play: Boolean) {
            val wasPlaying = isPlaying
            val oldState = state
            val oldPlay = playWhenReady
            state = value
            playWhenReady = play
            if (state != oldState) listeners.toList().forEach { it.onPlaybackStateChanged(state) }
            if (playWhenReady != oldPlay) listeners.toList().forEach {
                it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
            if (isPlaying != wasPlaying) listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        fun suppress(reason: Int) {
            suppression = reason
            listeners.toList().forEach { it.onPlaybackSuppressionReasonChanged(reason); it.onIsPlayingChanged(isPlaying) }
        }
    }
}
