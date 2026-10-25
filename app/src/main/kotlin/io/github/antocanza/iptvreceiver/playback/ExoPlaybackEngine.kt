package io.github.antocanza.iptvreceiver.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.antocanza.iptvreceiver.data.StreamDataSourceFactory
import io.github.antocanza.iptvreceiver.model.PlaybackSource

@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlaybackEngine(context: Context, source: PlaybackSource) : PlaybackEngine {
    override val player: ExoPlayer

    init {
        val http = DefaultHttpDataSource.Factory().setUserAgent("IPTVReceiver/0.1")
            .setAllowCrossProtocolRedirects(source.allowHttpStreams)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(StreamDataSourceFactory(http, source.allowHttpStreams)))
            .build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.fromUri(source.url))
            }
    }

    override fun prepareAndPlay() { player.prepare(); player.playWhenReady = true }
    override fun pause() = player.pause()
    override fun release() = player.release()
}
