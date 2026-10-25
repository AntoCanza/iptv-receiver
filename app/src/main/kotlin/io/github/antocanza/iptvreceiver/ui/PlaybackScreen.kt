package io.github.antocanza.iptvreceiver.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.InsecureStreamException
import io.github.antocanza.iptvreceiver.data.StreamDataSourceFactory
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(source: PlaybackSource) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(source) { mutableStateOf(false) }
    var buffering by remember(source) { mutableStateOf(true) }
    var insecureStream by remember(source) { mutableStateOf(false) }
    val player = remember(source) {
        val http = DefaultHttpDataSource.Factory().setUserAgent("IPTVReceiver/0.1")
            .setAllowCrossProtocolRedirects(source.allowHttpStreams).setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
        val guarded = StreamDataSourceFactory(http, source.allowHttpStreams)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(guarded)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(source.url))
        }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) {
                insecureStream = generateSequence(error as Throwable) { it.cause }.any { it is InsecureStreamException }
                failed = true
                buffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; setShowSubtitleButton(true) } },
            modifier = Modifier.fillMaxSize(), onRelease = { it.player = null })
        if (buffering && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (insecureStream) FailureReason.INSECURE_STREAM.localized() else stringResource(R.string.playback_failed),
                color = MaterialTheme.colorScheme.error)
            Button(onClick = { failed = false; buffering = true; player.prepare(); player.play() }) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
