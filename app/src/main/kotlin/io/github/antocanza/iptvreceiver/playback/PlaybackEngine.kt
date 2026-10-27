package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackRecoveryState { NONE, RECONNECTING, FAILED }

/** The retained playback session owns resources; Android views only attach a video surface. */
interface PlaybackEngine {
    val player: Player
    val recoveryState: StateFlow<PlaybackRecoveryState>
    fun prepareAndPlay()
    fun pause()
    fun release()
}
