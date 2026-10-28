package io.github.antocanza.iptvreceiver.playback

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ui.PlaybackViewModel
import java.util.UUID

/** The system owns the floating window. This controller only binds the current player and actions. */
class PlaybackPictureInPicture(private val activity: ComponentActivity) : DefaultLifecycleObserver, Player.Listener {
    var presenting by mutableStateOf(false)
        private set
    val supported: Boolean = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    private var model: PlaybackViewModel? = null
    private var sourceView: View? = null
    private var token = ""
    private var started = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var screenOff = false
    private var released = false
    private val actions = mutableMapOf<Int, PendingIntent>()
    private val actionName = "${activity.packageName}.PICTURE_IN_PICTURE_CONTROL"
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    model?.pause()
                    update()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    screenOff = false
                    update() // Unlock never resumes playback.
                }
                actionName -> {
                    if (intent.getStringExtra("session") != token || !activity.isInPictureInPictureMode ||
                        !started || !unlocked()) return
                    val current = model ?: return
                    val player = current.player
                    when (intent.getIntExtra("command", -1)) {
                        PLAY -> {
                            if (player.playerError != null || player.playbackState == Player.STATE_IDLE ||
                                (current.isLive && player.playbackState == Player.STATE_ENDED)) current.retry()
                            else {
                                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                                player.play()
                            }
                        }
                        PAUSE -> current.pause()
                        BACKWARD, FORWARD -> if (canSeek(current)) {
                            val offset = if (intent.getIntExtra("command", -1) == BACKWARD) -10_000L else 10_000L
                            val end = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + offset).coerceIn(0, end))
                        }
                    }
                }
            }
        }
    }

    init {
        activity.lifecycle.addObserver(this)
        ContextCompat.registerReceiver(activity, receiver, IntentFilter().apply {
            addAction(actionName)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun attach(value: PlaybackViewModel) {
        if (model === value) return
        model?.pause()
        model?.player?.removeListener(this)
        cancelActions()
        token = UUID.randomUUID().toString()
        model = value
        value.player.addListener(this)
        update()
    }

    fun detach(value: PlaybackViewModel) {
        if (model !== value) return
        // Navigation can remove the visible player while the Activity stays STARTED
        // (for example, opening Downloads from its notification in this single task).
        value.onHostStopped(activity.isChangingConfigurations)
        value.player.removeListener(this)
        model = null
        cancelActions()
        update()
    }

    fun setSourceView(view: View) {
        if (sourceView === view) return
        sourceView?.removeOnLayoutChangeListener(layoutListener)
        sourceView = view
        view.addOnLayoutChangeListener(layoutListener)
        update()
    }

    fun clearSourceView(view: View) {
        if (sourceView !== view) return
        view.removeOnLayoutChangeListener(layoutListener)
        sourceView = null
    }

    fun enter(showFailure: Boolean = true) {
        val allowed = supported && started && unlocked() && model != null && !activity.isFinishing
        val entered = allowed && runCatching { activity.enterPictureInPictureMode(parameters()) }.getOrDefault(false)
        if (!entered && showFailure) Toast.makeText(activity, R.string.player_pip_unavailable, Toast.LENGTH_LONG).show()
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && autoEnter()) enter(showFailure = false)
    }

    fun onModeChanged(inPip: Boolean) {
        presenting = inPip
        update()
    }

    fun onEntering() { presenting = true }

    override fun onStart(owner: LifecycleOwner) { started = true; update() }
    override fun onResume(owner: LifecycleOwner) {
        if (!activity.isInPictureInPictureMode) presenting = false
        update()
    }
    override fun onStop(owner: LifecycleOwner) {
        started = false
        // PiP remains STARTED while visible. Closing it or locking the phone reaches STOPPED.
        model?.onHostStopped(activity.isChangingConfigurations)
        update()
    }
    override fun onDestroy(owner: LifecycleOwner) {
        released = true
        model?.player?.removeListener(this)
        model = null
        sourceView?.removeOnLayoutChangeListener(layoutListener)
        sourceView = null
        cancelActions()
        activity.unregisterReceiver(receiver)
        activity.lifecycle.removeObserver(this)
    }
    override fun onEvents(player: Player, events: Player.Events) = update()

    fun update() {
        if (released || !supported || activity.isFinishing || activity.isDestroyed) return
        // An OEM may disable PiP or the user may revoke it while the app is open.
        runCatching { activity.setPictureInPictureParams(parameters()) }
    }

    private fun unlocked(): Boolean = !screenOff &&
        activity.getSystemService(PowerManager::class.java).isInteractive &&
        !activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun autoEnter(): Boolean {
        val current = model ?: return false
        val player = current.player
        return started && unlocked() && shouldAutoEnterPictureInPicture(player.playWhenReady,
            player.playbackState, player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            current.recoveryState.value)
    }

    private fun parameters(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val current = model
        if (current != null) {
            val player = current.player
            val size = player.videoSize
            builder.setAspectRatio(Rational(pictureInPictureAspect(size.width, size.height,
                size.pixelWidthHeightRatio, current.display.value.aspect.ratio), 10_000))
            val bounds = Rect()
            if (sourceView?.getGlobalVisibleRect(bounds) == true && !bounds.isEmpty) builder.setSourceRectHint(bounds)
            val controls = mutableListOf<RemoteAction>()
            if (canSeek(current)) controls += action(BACKWARD, android.R.drawable.ic_media_rew, R.string.player_pip_back)
            controls += if (showPictureInPicturePause(player.playWhenReady, player.playbackState,
                    player.playerError != null, current.recoveryState.value))
                action(PAUSE, android.R.drawable.ic_media_pause, R.string.player_pause)
            else action(PLAY, android.R.drawable.ic_media_play, R.string.play)
            if (canSeek(current)) controls += action(FORWARD, android.R.drawable.ic_media_ff, R.string.player_pip_forward)
            builder.setActions(controls.take(activity.maxNumPictureInPictureActions))
        } else builder.setActions(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(current != null && autoEnter()).setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun canSeek(current: PlaybackViewModel): Boolean = !current.isLive &&
        current.player.isCurrentMediaItemSeekable && current.player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

    private fun action(command: Int, icon: Int, label: Int): RemoteAction {
        val pending = actions.getOrPut(command) {
            PendingIntent.getBroadcast(activity, command, Intent(actionName).setPackage(activity.packageName)
                .putExtra("session", token).putExtra("command", command),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val text = activity.getString(label)
        return RemoteAction(Icon.createWithResource(activity, icon), text, text, pending)
    }

    private fun cancelActions() { actions.values.forEach { it.cancel() }; actions.clear() }

    private companion object {
        const val PLAY = 1
        const val PAUSE = 2
        const val BACKWARD = 3
        const val FORWARD = 4
    }
}
