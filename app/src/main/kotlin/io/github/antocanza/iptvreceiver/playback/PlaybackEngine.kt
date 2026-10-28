package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackRecoveryState { NONE, RECONNECTING, FAILED }

/** The retained playback session owns resources; Android views only attach a video surface. */
interface PlaybackEngine {
    val isLive: Boolean get() = false
    val player: Player
    val recoveryState: StateFlow<PlaybackRecoveryState>
    val keepScreenOn: StateFlow<Boolean>
    fun prepareAndPlay()
    fun pause()
    fun release()
}
