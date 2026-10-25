package io.github.antocanza.iptvreceiver.ui

import android.view.WindowManager
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.InsecureStreamException
import io.github.antocanza.iptvreceiver.model.FailureReason
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(model: PlaybackViewModel, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    val player = model.player
    var failed by remember(player) { mutableStateOf(player.playerError != null) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var insecureStream by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) {
                insecureStream = generateSequence(error as Throwable) { it.cause }.any { it is InsecureStreamException }
                failed = true
                buffering = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.onHostStopped(activity?.isChangingConfigurations == true)
        }
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (activity?.isChangingConfigurations == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        } else bars?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply {
            this.player = player
            useController = true
            setShowSubtitleButton(true)
            setFullscreenButtonState(fullscreen)
            setFullscreenButtonClickListener(onFullscreenChange)
        } }, modifier = Modifier.fillMaxSize(), update = {
            // Media3 reports programmatic state changes to its click listener too.
            // Suppress that callback so an automatic rotation never locks orientation.
            it.setFullscreenButtonClickListener(null)
            it.setFullscreenButtonState(fullscreen)
            it.setFullscreenButtonClickListener(onFullscreenChange)
        }, onRelease = { it.setFullscreenButtonClickListener(null); it.player = null })
        if (buffering && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (insecureStream) FailureReason.INSECURE_STREAM.localized() else stringResource(R.string.playback_failed),
                color = MaterialTheme.colorScheme.error)
            Button(onClick = { failed = false; buffering = true; model.retry() }) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
