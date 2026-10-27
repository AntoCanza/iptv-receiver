package io.github.antocanza.iptvreceiver.ui

import android.view.WindowManager
import android.view.View
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.InvalidStreamSchemeException
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.playback.PlaybackRecoveryState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlaybackScreen(model: PlaybackViewModel, fullscreen: Boolean, onFullscreenChange: (Boolean) -> Unit,
    onGuide: (() -> Unit)? = null) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    val player = model.player
    val recoveryState by model.recoveryState.collectAsStateWithLifecycle()
    val reconnecting = recoveryState == PlaybackRecoveryState.RECONNECTING
    val display by model.display.collectAsStateWithLifecycle()
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var failed by remember(player) { mutableStateOf(player.playerError != null) }
    var buffering by remember(player) { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var invalidStreamScheme by remember(player) { mutableStateOf(false) }
    var decodingFailure by remember(player) { mutableStateOf(isDecodingFailure(player.playerError)) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) {
                    failed = false
                    invalidStreamScheme = false
                    decodingFailure = false
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                invalidStreamScheme = generateSequence(error as Throwable) { it.cause }.any { it is InvalidStreamSchemeException }
                decodingFailure = isDecodingFailure(error)
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
        AndroidView(factory = { DisplayPlayerView(it).apply {
            this.player = player
            setEnableComposeSurfaceSyncWorkaround(true)
            useController = true
            setShowSubtitleButton(false)
            findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
            setVideoDisplay(display)
            setFullscreenButtonState(fullscreen)
            setFullscreenButtonClickListener(onFullscreenChange)
        } }, modifier = Modifier.fillMaxSize(), update = {
            if (it.player !== player) it.player = player
            val controllerView = it
            it.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = controllerView.isControllerFullyVisible })
            controlsVisible = it.isControllerFullyVisible
            it.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
            it.setVideoDisplay(display)
            // Media3 reports programmatic state changes to its click listener too.
            // Suppress that callback so an automatic rotation never locks orientation.
            it.setFullscreenButtonClickListener(null)
            it.setFullscreenButtonState(fullscreen)
            it.setFullscreenButtonClickListener(onFullscreenChange)
        }, onRelease = { it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?); it.setFullscreenButtonClickListener(null); it.player = null })
        PlaybackTools(model, onGuide, controlsVisible)
        if (reconnecting || (buffering && !failed)) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (!reconnecting && (failed || recoveryState == PlaybackRecoveryState.FAILED)) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(when {
                invalidStreamScheme -> FailureReason.INVALID_STREAM.localized()
                decodingFailure -> stringResource(R.string.playback_decoding_failed)
                else -> stringResource(R.string.playback_failed)
            },
                color = MaterialTheme.colorScheme.error)
            Button(onClick = { failed = false; buffering = true; model.retry() }) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private fun isDecodingFailure(error: PlaybackException?): Boolean = error?.errorCode in setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)
