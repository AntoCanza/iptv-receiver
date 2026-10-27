package io.github.antocanza.iptvreceiver.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import io.github.antocanza.iptvreceiver.data.StreamDataSourceFactory
import io.github.antocanza.iptvreceiver.data.LiveManifestSession
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlaybackEngine(context: Context, source: PlaybackSource, onFirstPlayback: () -> Unit = {}) : PlaybackEngine {
    override val player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recovery: LivePlaybackRecovery
    override val recoveryState get() = recovery.state

    init {
        val mediaItem = MediaItem.Builder().setUri(source.url).apply {
            if (source.isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(30_000).build())
        }.build()
        val http = DefaultHttpDataSource.Factory().setUserAgent("IPTVReceiver/0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(if (source.isLive) 5_000 else 15_000)
            .setReadTimeoutMs(if (source.isLive) 5_000 else 30_000)
        val mediaSources = DefaultMediaSourceFactory(StreamDataSourceFactory(http))
        if (source.isLive) mediaSources.setLoadErrorHandlingPolicy(LiveLoadErrorPolicy())
        val loadControl = if (source.isLive) DefaultLoadControl.Builder()
            .setBufferDurationsMsForStreaming(60_000, 90_000, 20_000, 20_000)
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholdsForStreaming(false)
            .build() else DefaultLoadControl()
        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSources)
            .setLoadControl(loadControl)
            .build().apply {
                addListener(FirstPlaybackListener(onFirstPlayback))
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
            }
        fun setSource() {
            if (source.isLive && mediaItem.localConfiguration?.uri?.path?.endsWith(".m3u8", ignoreCase = true) == true) {
                val session = LiveManifestSession(source.url)
                val hls = HlsMediaSource.Factory(HlsDataSourceFactory { dataType ->
                    StreamDataSourceFactory(http, session.takeIf { dataType == C.DATA_TYPE_MANIFEST }).createDataSource()
                }).setPlaylistParserFactory(SessionHlsPlaylistParserFactory(session))
                    .setLoadErrorHandlingPolicy(LiveLoadErrorPolicy())
                player.setMediaSource(hls.createMediaSource(mediaItem), true)
            } else player.setMediaItem(mediaItem, true)
        }
        setSource()
        recovery = LivePlaybackRecovery(player, source.isLive, scope, reloadStream = {
            setSource()
            player.prepare()
        })
    }

    override fun prepareAndPlay() = recovery.prepareAndPlay()
    override fun pause() = recovery.pause()
    override fun release() {
        recovery.release()
        scope.cancel()
        player.release()
    }
}
