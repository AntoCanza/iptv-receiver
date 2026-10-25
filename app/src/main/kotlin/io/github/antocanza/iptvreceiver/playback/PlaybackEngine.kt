package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player

/** The retained playback session owns resources; Android views only attach a video surface. */
interface PlaybackEngine {
    val player: Player
    fun prepareAndPlay()
    fun pause()
    fun release()
}
