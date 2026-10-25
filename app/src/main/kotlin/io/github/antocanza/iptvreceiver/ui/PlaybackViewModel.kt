package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import io.github.antocanza.iptvreceiver.playback.PlaybackEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackViewModel(private val engine: PlaybackEngine, landscape: Boolean) : ViewModel() {
    val player get() = engine.player
    private var lastLandscape = landscape
    private val mutableFullscreen = MutableStateFlow(landscape)
    val fullscreen = mutableFullscreen.asStateFlow()

    init { engine.prepareAndPlay() }

    fun onOrientationChanged(landscape: Boolean) {
        if (lastLandscape != landscape) {
            lastLandscape = landscape
            mutableFullscreen.value = landscape
        }
    }

    fun setFullscreen(fullscreen: Boolean) { mutableFullscreen.value = fullscreen }

    fun onHostStopped(changingConfigurations: Boolean) {
        if (!changingConfigurations) engine.pause()
    }

    fun retry() = engine.prepareAndPlay()

    override fun onCleared() { engine.release() }
}
