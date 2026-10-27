package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.playback.PlaybackEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VideoScale { FIT, ZOOM, STRETCH }
enum class VideoAspect(val ratio: Float?) { AUTO(null), WIDE(16f / 9), CLASSIC(4f / 3), CINEMA(21f / 9) }
data class VideoDisplay(val scale: VideoScale = VideoScale.FIT, val aspect: VideoAspect = VideoAspect.AUTO)

class PlaybackViewModel(private val engine: PlaybackEngine, landscape: Boolean, startPositionMs: Long = 0,
    private val onProgress: ((PlaybackProgress) -> Unit)? = null) : ViewModel() {
    val player get() = engine.player
    val recoveryState get() = engine.recoveryState
    private var lastLandscape = landscape
    private val mutableFullscreen = MutableStateFlow(landscape)
    val fullscreen = mutableFullscreen.asStateFlow()
    private val mutableDisplay = MutableStateFlow(VideoDisplay())
    val display = mutableDisplay.asStateFlow()

    private var started = false
    private var progressJob: Job? = null
    private var lastProgress: PlaybackProgress? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) started = true
            progressJob?.cancel()
            if (isPlaying) progressJob = viewModelScope.launch {
                while (isActive) { saveProgress(); delay(5_000) }
            }
            saveProgress()
        }
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_TIMELINE_CHANGED)) saveProgress()
        }
    }

    init {
        if (startPositionMs > 0) player.seekTo(startPositionMs)
        if (onProgress != null) player.addListener(listener)
        engine.prepareAndPlay()
    }

    private fun saveProgress() {
        val callback = onProgress ?: return
        if (!started) return
        val progress = PlaybackProgress.checkpoint(player.currentPosition, player.duration, player.playbackState == Player.STATE_ENDED)
        if (progress != lastProgress) { lastProgress = progress; callback(progress) }
    }

    fun onOrientationChanged(landscape: Boolean) {
        if (lastLandscape != landscape) {
            lastLandscape = landscape
            mutableFullscreen.value = landscape
        }
    }

    fun setFullscreen(fullscreen: Boolean) { mutableFullscreen.value = fullscreen }
    fun setDisplay(display: VideoDisplay) { mutableDisplay.value = display }

    fun onHostStopped(changingConfigurations: Boolean) {
        if (!changingConfigurations) { saveProgress(); engine.pause() }
    }

    fun retry() = engine.prepareAndPlay()

    override fun onCleared() {
        saveProgress()
        progressJob?.cancel()
        if (onProgress != null) player.removeListener(listener)
        engine.release()
    }
}
