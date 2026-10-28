package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Retained with the player so rotation cannot renew an interrupted stream's grace period. */
internal class PlaybackScreenAwake(
    private val player: Player,
    private val recoveryState: StateFlow<PlaybackRecoveryState>,
    private val scope: CoroutineScope,
) : Player.Listener {
    private val mutableKeepScreenOn = MutableStateFlow(false)
    val keepScreenOn = mutableKeepScreenOn.asStateFlow()
    private var waitingJob: Job? = null
    private var waitingExpired = false
    private val recoveryJob: Job

    init {
        player.addListener(this)
        recoveryJob = scope.launch { recoveryState.collect { update() } }
        update()
    }

    override fun onPlaybackStateChanged(playbackState: Int) = update()
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = update()
    override fun onIsPlayingChanged(isPlaying: Boolean) = update()
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = update()

    fun onPlaybackRequested() {
        resetWaiting()
        update()
    }

    fun release() {
        player.removeListener(this)
        recoveryJob.cancel()
        resetWaiting()
        mutableKeepScreenOn.value = false
    }

    private fun update() {
        val recovering = recoveryState.value == PlaybackRecoveryState.RECONNECTING
        val waiting = player.playWhenReady &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            recoveryState.value != PlaybackRecoveryState.FAILED &&
            (player.playbackState == Player.STATE_BUFFERING || recovering)
        when {
            player.isPlaying -> {
                resetWaiting()
                mutableKeepScreenOn.value = true
            }
            waiting -> {
                mutableKeepScreenOn.value = !waitingExpired
                if (waitingJob == null && !waitingExpired) waitingJob = scope.launch {
                    delay(WAITING_GRACE_MS)
                    waitingJob = null
                    waitingExpired = true
                    mutableKeepScreenOn.value = false
                }
            }
            else -> {
                resetWaiting()
                mutableKeepScreenOn.value = false
            }
        }
    }

    private fun resetWaiting() {
        waitingJob?.cancel()
        waitingJob = null
        waitingExpired = false
    }

    private companion object {
        const val WAITING_GRACE_MS = 60_000L
    }
}
