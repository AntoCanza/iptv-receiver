package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import io.github.antocanza.iptvreceiver.data.InvalidStreamSchemeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Lives with the retained player, so rotation cannot duplicate reconnects. All calls use its main thread. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class LivePlaybackRecovery(
    private val player: Player,
    private val isLive: Boolean,
    private val scope: CoroutineScope,
    private val reloadStream: () -> Unit,
) : Player.Listener {
    private val mutableState = MutableStateFlow(PlaybackRecoveryState.NONE)
    val state = mutableState.asStateFlow()
    private var reconnectJob: Job? = null
    private var bufferingJob: Job? = null
    private var stablePlaybackJob: Job? = null
    private var attempts = 0
    private var released = false

    init { player.addListener(this) }

    fun prepareAndPlay() {
        cancelPending()
        attempts = 0
        mutableState.value = PlaybackRecoveryState.NONE
        if (isLive) reloadStream() else player.prepare()
        player.play()
    }

    fun pause() {
        cancelPending()
        mutableState.value = PlaybackRecoveryState.NONE
        player.pause()
    }

    fun release() {
        released = true
        cancelPending()
        player.removeListener(this)
    }

    override fun onPlayerError(error: PlaybackException) {
        if (!isLive || released) return
        if (isRetryable(error)) reconnect() else {
            cancelPending()
            mutableState.value = PlaybackRecoveryState.FAILED
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!isLive || released) return
        bufferingJob?.cancel()
        bufferingJob = null
        when (playbackState) {
            Player.STATE_IDLE -> Unit // An error's queued reconnect must survive its IDLE event.
            Player.STATE_ENDED -> reconnect()
            Player.STATE_BUFFERING -> watchBuffering()
            Player.STATE_READY -> {
                reconnectJob?.cancel()
                reconnectJob = null
                mutableState.value = PlaybackRecoveryState.NONE
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!isLive || released) return
        if (!playWhenReady) {
            cancelPending()
            mutableState.value = PlaybackRecoveryState.NONE
        } else when (player.playbackState) {
            Player.STATE_IDLE, Player.STATE_READY -> Unit
            Player.STATE_ENDED -> reconnect()
            Player.STATE_BUFFERING -> watchBuffering()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isLive || released) return
        stablePlaybackJob?.cancel()
        stablePlaybackJob = null
        if (isPlaying) {
            // Brief READY transitions during an outage must not create an unlimited retry loop.
            stablePlaybackJob = scope.launch {
                delay(STABLE_PLAYBACK_MS)
                if (player.isPlaying) attempts = 0
            }
        }
    }

    private fun watchBuffering() {
        if (!player.playWhenReady || bufferingJob != null || reconnectJob != null ||
            mutableState.value == PlaybackRecoveryState.FAILED) return
        bufferingJob = scope.launch {
            delay(BUFFERING_TIMEOUT_MS)
            bufferingJob = null
            if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) reconnect()
        }
    }

    private fun reconnect() {
        if (released || !player.playWhenReady || reconnectJob != null) return
        bufferingJob?.cancel()
        bufferingJob = null
        stablePlaybackJob?.cancel()
        stablePlaybackJob = null
        if (attempts >= RETRY_DELAYS_MS.size) {
            mutableState.value = PlaybackRecoveryState.FAILED
            return
        }
        val retryDelay = RETRY_DELAYS_MS[attempts++]
        mutableState.value = PlaybackRecoveryState.RECONNECTING
        reconnectJob = scope.launch {
            // Defer mutations until Media3 has finished delivering its error -> IDLE events.
            delay(retryDelay)
            reconnectJob = null
            if (!released && player.playWhenReady) {
                // Recreate the source at its default position. prepare() alone can retain EOF or
                // a stale HLS playlist; resuming a progressive live stream's byte offset also fails.
                reloadStream()
                if (player.playbackState == Player.STATE_BUFFERING) watchBuffering()
            }
        }
    }

    private fun cancelPending() {
        reconnectJob?.cancel()
        reconnectJob = null
        bufferingJob?.cancel()
        bufferingJob = null
        stablePlaybackJob?.cancel()
        stablePlaybackJob = null
    }

    private fun isRetryable(error: PlaybackException): Boolean {
        val causes = generateSequence(error as Throwable) { it.cause }.toList()
        if (causes.any { it is InvalidStreamSchemeException || it is SSLHandshakeException ||
            it is SSLPeerUnverifiedException || it is CertificateException }) return false
        val response = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
        if (response != null) return response.responseCode in setOf(404, 408, 410, 429) || response.responseCode in 500..599
        return error.errorCode in setOf(
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        )
    }

    private companion object {
        val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 8_000)
        const val BUFFERING_TIMEOUT_MS = 30_000L
        const val STABLE_PLAYBACK_MS = 10_000L
    }
}
