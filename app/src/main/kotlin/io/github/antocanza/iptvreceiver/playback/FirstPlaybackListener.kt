package io.github.antocanza.iptvreceiver.playback

import androidx.media3.common.Player

/** Failed loads and buffering never become watched entries; rotation/retry cannot duplicate one. */
internal class FirstPlaybackListener(private val onFirstPlayback: () -> Unit) : Player.Listener {
    private var recorded = false
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && !recorded) {
            recorded = true
            onFirstPlayback()
        }
    }
}
