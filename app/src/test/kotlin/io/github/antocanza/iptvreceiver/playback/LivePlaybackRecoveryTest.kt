package io.github.antocanza.iptvreceiver.playback

import android.os.Bundle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.antocanza.iptvreceiver.data.InvalidStreamSchemeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

@OptIn(ExperimentalCoroutinesApi::class)
class LivePlaybackRecoveryTest {
    @Test fun `live EOF reopens a fresh source once and clears recovery when ready`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        session.player.ready()
        session.player.position = 42_000
        session.player.changeState(Player.STATE_ENDED)
        session.recovery.onPlaybackStateChanged(Player.STATE_ENDED)
        assertEquals(PlaybackRecoveryState.RECONNECTING, session.recovery.state.value)
        advance(999)
        assertEquals(1, session.reloads)
        advance(1)
        assertEquals(2, session.reloads)
        assertEquals(0L, session.player.position)
        assertTrue(session.player.playWhenReady)
        session.player.ready()
        assertEquals(PlaybackRecoveryState.NONE, session.recovery.state.value)
    }

    @Test fun `network timeout disconnect and expired live window recover after error then IDLE`() = runTest {
        for ((code, cause) in listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT to null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to null,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW to null,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to IOException(SSLException("Synthetic peer closed connection")),
        )) {
            val session = session()
            session.recovery.prepareAndPlay()
            session.player.ready()
            session.player.fail(code, cause)
            assertEquals(Player.STATE_IDLE, session.player.state)
            assertEquals(1, session.reloads)
            advance(1_000)
            assertEquals(2, session.reloads)
            session.player.ready()
            assertEquals(PlaybackRecoveryState.NONE, session.recovery.state.value)
            session.recovery.release()
        }
    }

    @Test fun `buffering watchdog also covers reconnects that remain in BUFFERING`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        advance(29_999)
        assertEquals(1, session.reloads)
        advance(1)
        assertEquals(PlaybackRecoveryState.RECONNECTING, session.recovery.state.value)
        advance(1_000)
        assertEquals(2, session.reloads)
        advance(30_000)
        advance(2_000)
        assertEquals(3, session.reloads)
        session.player.ready()
        advance(60_000)
        assertEquals(3, session.reloads)
    }

    @Test fun `temporary buffering clears its watchdog without reopening a healthy stream`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        advance(20_000)
        session.player.ready()
        advance(60_000)
        assertEquals(1, session.reloads)
    }

    @Test fun `controls pause audio focus loss and background pause cancel a queued reconnect`() = runTest {
        for (reason in listOf(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
            Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) {
            val session = session()
            session.recovery.prepareAndPlay()
            session.player.changeState(Player.STATE_ENDED)
            session.player.changePlayWhenReady(false, reason)
            advance(60_000)
            assertEquals(1, session.reloads)
            assertFalse(session.player.playWhenReady)
            assertEquals(PlaybackRecoveryState.NONE, session.recovery.state.value)
            session.recovery.release()
        }
        val session = session()
        session.recovery.prepareAndPlay()
        session.player.fail(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        session.recovery.pause()
        advance(60_000)
        assertEquals(1, session.reloads)
        assertFalse(session.player.playWhenReady)
    }

    @Test fun `leaving cancels reconnects and removes the retained listener`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        session.player.changeState(Player.STATE_ENDED)
        session.recovery.release()
        advance(60_000)
        assertEquals(1, session.reloads)
        assertTrue(session.player.listeners.isEmpty())
    }

    @Test fun `repeated failed or briefly ready attempts back off and stop with manual retry available`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        for ((index, delay) in listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L).withIndex()) {
            session.player.ready()
            advance(100)
            session.player.changeState(Player.STATE_ENDED)
            advance(delay - 1)
            assertEquals(index + 1, session.reloads)
            advance(1)
            assertEquals(index + 2, session.reloads)
        }
        session.player.changeState(Player.STATE_ENDED)
        advance(60_000)
        assertEquals(6, session.reloads)
        assertEquals(PlaybackRecoveryState.FAILED, session.recovery.state.value)
        session.recovery.prepareAndPlay()
        assertEquals(7, session.reloads)
        assertEquals(PlaybackRecoveryState.NONE, session.recovery.state.value)
        session.player.changeState(Player.STATE_ENDED)
        advance(1_000)
        assertEquals(8, session.reloads)
    }

    @Test fun `successful playback renews retry budget across recurring provider disconnects`() = runTest {
        val session = session()
        session.recovery.prepareAndPlay()
        repeat(8) {
            session.player.ready()
            advance(15_000)
            session.player.changeState(Player.STATE_ENDED)
            advance(1_000)
        }
        assertEquals(9, session.reloads)
        assertEquals(PlaybackRecoveryState.RECONNECTING, session.recovery.state.value)
    }

    @Test fun `unsupported decoder invalid scheme and TLS failures are not automatically retried`() = runTest {
        for ((code, cause) in listOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED to IOException(InvalidStreamSchemeException()),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to IOException(SSLHandshakeException("Synthetic TLS failure")),
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION to null,
        )) {
            val session = session()
            session.recovery.prepareAndPlay()
            session.player.fail(code, cause)
            advance(60_000)
            assertEquals(1, session.reloads)
            assertEquals(PlaybackRecoveryState.FAILED, session.recovery.state.value)
            session.recovery.release()
        }
    }

    @Test fun `movies and episodes do not loop reconnect or gain a buffering watchdog`() = runTest {
        val session = session(isLive = false)
        session.recovery.prepareAndPlay()
        assertEquals(1, session.player.prepareCalls)
        session.player.changeState(Player.STATE_ENDED)
        advance(60_000)
        session.player.fail(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        advance(60_000)
        session.player.changeState(Player.STATE_BUFFERING)
        advance(60_000)
        assertEquals(0, session.reloads)
        assertEquals(PlaybackRecoveryState.NONE, session.recovery.state.value)
        session.recovery.prepareAndPlay()
        assertEquals(2, session.player.prepareCalls)
    }

    private fun TestScope.advance(ms: Long) { advanceTimeBy(ms); runCurrent() }

    private fun TestScope.session(isLive: Boolean = true): Session {
        val session = Session()
        session.recovery = LivePlaybackRecovery(session.player.proxy, isLive, backgroundScope, reloadStream = {
            session.reloads++
            session.player.position = 0
            session.player.changeState(Player.STATE_BUFFERING)
        })
        return session
    }

    private class Session {
        val player = FakePlayer()
        var reloads = 0
        lateinit var recovery: LivePlaybackRecovery
    }

    /** Sends Media3's event order, including no state callback when BUFFERING remains unchanged. */
    private class FakePlayer {
        val listeners = mutableListOf<Player.Listener>()
        var state = Player.STATE_IDLE
        var playWhenReady = false
        var position = 0L
        var prepareCalls = 0
        private val isPlaying get() = playWhenReady && state == Player.STATE_READY
        val proxy = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "addListener" -> { listeners.add(args!![0] as Player.Listener); null }
                "removeListener" -> { listeners.remove(args!![0] as Player.Listener); null }
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackState" -> state
                "isPlaying" -> isPlaying
                "prepare" -> { prepareCalls++; changeState(Player.STATE_BUFFERING); null }
                "play" -> { changePlayWhenReady(true); null }
                "pause" -> { changePlayWhenReady(false); null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player

        fun ready() = changeState(Player.STATE_READY)

        fun changeState(value: Int) {
            val wasPlaying = isPlaying
            if (state != value) {
                state = value
                listeners.toList().forEach { it.onPlaybackStateChanged(value) }
            }
            if (isPlaying != wasPlaying) listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        fun changePlayWhenReady(value: Boolean, reason: Int = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            val wasPlaying = isPlaying
            if (playWhenReady != value) {
                playWhenReady = value
                listeners.toList().forEach { it.onPlayWhenReadyChanged(value, reason) }
            }
            if (isPlaying != wasPlaying) listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        fun fail(code: Int, cause: Throwable? = null) {
            val error = SyntheticPlaybackException(code, cause)
            listeners.toList().forEach { it.onPlayerError(error) }
            changeState(Player.STATE_IDLE)
        }
    }

    // Supply a timestamp directly so these state-transition tests need no Android clock/runtime.
    private class SyntheticPlaybackException(code: Int, cause: Throwable?) :
        PlaybackException("Synthetic stream failure", cause, code, Bundle.EMPTY, 0L)
}
