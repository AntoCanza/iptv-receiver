package io.github.antocanza.iptvreceiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.PowerManager
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.data.DownloadEntry
import io.github.antocanza.iptvreceiver.data.DownloadStatus
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.playback.ExoPlaybackEngine
import io.github.antocanza.iptvreceiver.ui.DisplayPlayerView
import io.github.antocanza.iptvreceiver.ui.PlaybackScreen
import io.github.antocanza.iptvreceiver.ui.PlaybackViewModel
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Opt-in hardware checks. Real phases use the saved account through normal navigation. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PictureInPictureDeviceChecks(
    private val instrumentation: Instrumentation,
    private val report: (String) -> Unit,
    private val passed: (String) -> Unit,
) {
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as ReceiverApplication
    private val automation = instrumentation.uiAutomation
    @Volatile private lateinit var activity: MainActivity
    private lateinit var player: ExoPlayer
    private val frames = AtomicInteger()
    private var appWindowId = -1
    private var fixture: ExoPlaybackEngine? = null
    private var offlineId: String? = null
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(value: Activity, state: Bundle?) { if (value is MainActivity) activity = value }
        override fun onActivityResumed(value: Activity) { if (value is MainActivity) activity = value }
        override fun onActivityStarted(value: Activity) {}
        override fun onActivityPaused(value: Activity) {}
        override fun onActivityStopped(value: Activity) {}
        override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
        override fun onActivityDestroyed(value: Activity) {}
    }

    fun run(phase: String, real: Boolean) {
        check(phase == "fixture" || real) { "Real-service checks require explicit opt-in" }
        app.registerActivityLifecycleCallbacks(callbacks)
        val serviceInfo = automation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = serviceInfo
        try {
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            when (phase) {
                "fixture" -> openFixture()
                "offline" -> openOffline()
                else -> openReal(phase.removePrefix("real-"))
            }
            waitFor(60_000) { view()?.player?.isPlaying == true }
            main {
                player = view()?.player as ExoPlayer
                player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() }
            }
            waitFor { frames.get() >= 10 }
            appWindowId = checkNotNull(automation.rootInActiveWindow).windowId
            passed("$phase: video decodes through the production player")
            main {
                val format = player.videoFormat
                report("PiP media: ${format?.sampleMimeType} ${format?.width}x${format?.height}; audio=${player.audioFormat?.sampleMimeType ?: "none"}")
            }
            when (phase) {
                "lock" -> lockChecks()
                "navigation" -> navigationChecks()
                else -> interactionChecks(phase)
            }
        } finally {
            if (::player.isInitialized) main { player.pause() }
            fixture?.let { value -> main { value.release() } }
            offlineId?.let { id -> runBlocking { app.downloads.remove(id) } }
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun openReal(kind: String) = runBlocking {
        report("PiP: waiting for authenticated home")
        waitUi(60_000) { visible(label(R.string.tv_guide)) }
        if (visible(label(R.string.subscription_reminder_title))) click(label(R.string.acknowledge))
        val connection = checkNotNull(app.connectionStore.load())
        val type = when (kind) { "live" -> ContentType.LIVE; "series" -> ContentType.SERIES; else -> ContentType.MOVIE }
        val query = when (type) { ContentType.LIVE -> "Rai 1"; ContentType.MOVIE -> "Interstellar"; ContentType.SERIES -> "Friends" }
        val candidates = app.repository.items(connection, type, null)
        val item = candidates.firstOrNull { it.title.equals(query, true) }
            ?: candidates.first { it.title.contains(query, true) }
        report("PiP: opening $type catalog through the normal UI")
        click(label(when (type) { ContentType.LIVE -> R.string.live_tv; ContentType.MOVIE -> R.string.movies; ContentType.SERIES -> R.string.series }))
        waitUi(60_000) { nodes().any { it.isEditable || it.className?.toString() == "android.widget.EditText" } }
        val search = nodes().first { it.isEditable || it.className?.toString() == "android.widget.EditText" }
        report("PiP: searching the $type catalog")
        check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }))
        fun catalogNode(node: AccessibilityNodeInfo) = node.isVisibleToUser && !node.isEditable &&
            node.className?.toString() != "android.widget.EditText" && node.text?.toString() == item.title
        waitUi(60_000) { nodes().any(::catalogNode) }
        clickNode(nodes().first(::catalogNode))
        report("PiP: selected $type item")
        if (type != ContentType.LIVE) {
            val resumePrefix = label(R.string.watch_resume_at).substringBefore('%')
            waitUi(60_000) {
                nodes().any { node -> node.isEnabled && node.isVisibleToUser && node.text?.toString()?.let {
                    it == label(R.string.play) || it == label(R.string.watch_again) ||
                        it.startsWith(resumePrefix)
                } == true }
            }
            val action = nodes().first { node -> node.isEnabled && node.isVisibleToUser && node.text?.toString()?.let {
                it == label(R.string.play) || it == label(R.string.watch_again) || it.startsWith(resumePrefix)
            } == true }
            clickNode(action)
        }
    }

    private fun openFixture() {
        val file = context.cacheDir.resolve("pip-fixture.mp4")
        file.writeBytes(downloadVideoFixture(context))
        main {
            val engine = ExoPlaybackEngine(context, PlaybackSource("offline", "PiP test", false), file.toUri())
            fixture = engine
            val model = PlaybackViewModel(engine, false)
            activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) { PlaybackScreen(model, false, {}) } }
        }
    }

    private fun openOffline(waitForHome: Boolean = true) = runBlocking {
        if (waitForHome) waitUi(60_000) { visible(label(R.string.tv_guide)) }
        val downloads = app.downloads
        val account = checkNotNull(downloads.account.value)
        val id = java.util.UUID.randomUUID().toString().replace("-", "").repeat(2)
        offlineId = id
        val media = downloadVideoFixture(context)
        val location = downloads.files.create(id, "mp4", null)
        downloads.files.output(location).open(0).use { it.write(media) }
        val title = "PiP offline fixture"
        downloads.store.put(DownloadEntry(id, account,
            WatchTarget(CatalogItem(id, title, ContentType.MOVIE, extension = "mp4")),
            emptyList(), location, DownloadStatus.READY, media.size.toLong(), media.size.toLong()))
        shell("am start -n ${context.packageName}/.MainActivity --ez open_downloads true")
        click(activity.getString(R.string.action_for_title, label(R.string.play), title))
    }

    private fun navigationChecks() {
        shell("input keyevent KEYCODE_HOME")
        waitFor { activity.isInPictureInPictureMode && player.isPlaying }
        SystemClock.sleep(800)
        // Match the launcher-style intent carried by the download notification.
        shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n ${context.packageName}/.MainActivity --ez open_downloads true")
        waitUi { visible(label(R.string.downloads)) }
        waitFor { !player.playWhenReady && !activity.isInPictureInPictureMode }
        SystemClock.sleep(1_000)
        main { check(!player.playWhenReady) }
        passed("navigation: opening Downloads from PiP pauses the removed player")
        openOffline(waitForHome = false)
        waitFor { view()?.player?.isPlaying == true }
        main { player = view()?.player as ExoPlayer }
        shell("input keyevent KEYCODE_HOME")
        waitFor { activity.isInPictureInPictureMode && player.isPlaying }
        SystemClock.sleep(800)
        shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n ${context.packageName}/.MainActivity --ez open_downloads true")
        waitUi { visible(label(R.string.downloads)) }
        waitFor { !player.playWhenReady && !activity.isInPictureInPictureMode && view() == null }
        passed("navigation: repeated Downloads intent leaves the offline player and opens the full list")
    }

    private fun interactionChecks(phase: String) {
        // Paused video should stay paused and should not produce a surprise floating window.
        main { player.pause() }
        shell("input keyevent KEYCODE_HOME")
        SystemClock.sleep(1_000)
        main { check(!activity.isInPictureInPictureMode && !player.playWhenReady) }
        reopen()
        main { check(view()?.player === player && !player.playWhenReady); player.play() }
        waitFor { player.isPlaying }
        passed("$phase: paused Home/reopen does not enter PiP or resume")

        val initialFrames = frames.get()
        shell("input keyevent KEYCODE_HOME")
        waitFor { activity.isInPictureInPictureMode && activity.pictureInPicture.presenting }
        waitFor { frames.get() > initialFrames + 10 && player.isPlaying }
        main { check(view()?.useController == false) }
        screenshot("$phase-enter")
        passed("$phase: Home enters a video-only PiP window without replacing the player")

        pipAction(label(R.string.player_pause))
        waitFor { !player.playWhenReady }
        SystemClock.sleep(400) // Allow the already submitted decoder frame to finish.
        val paused = position()
        val pausedFrames = frames.get()
        SystemClock.sleep(1_200)
        main { check(!player.playWhenReady && !player.isPlaying) }
        // A live HLS window slides even while paused, changing its relative position.
        // Frozen video frames, rather than that moving timeline origin, prove live pause.
        if (phase == "real-live") check(frames.get() <= pausedFrames + 1)
        else check(abs(position() - paused) < 400)
        if (phase != "real-live") {
            val before = position()
            pipAction(label(R.string.player_pip_forward))
            waitFor { player.currentPosition >= before + 9_000 }
            pipAction(label(R.string.player_pip_back))
            waitFor { abs(player.currentPosition - before) < 500 }
        } else check(!visible(label(R.string.player_pip_back)) && !visible(label(R.string.player_pip_forward)))
        pipAction(label(R.string.play))
        val previousFrames = frames.get()
        waitFor { player.isPlaying && frames.get() > previousFrames + 10 }
        passed("$phase: system PiP pause/resume${if (phase != "real-live") " and ±10-second seek" else " without VOD seek buttons"}")

        // Dismiss the menu, then use real touch gestures against the system's PiP window.
        shell("input keyevent KEYCODE_BACK")
        SystemClock.sleep(600)
        val beforeMove = bounds()
        val screen = checkNotNull(automation.takeScreenshot())
        val screenWidth = screen.width; val screenHeight = screen.height
        screen.recycle()
        drag(beforeMove.exactCenterX(), beforeMove.exactCenterY(), screenWidth * .25f, screenHeight * .25f)
        waitFor { val b = bounds(); abs(b.left - beforeMove.left) > 40 || abs(b.top - beforeMove.top) > 40 }
        SystemClock.sleep(800) // Let Android finish snapping the dragged window to an edge.
        val beforeSize = bounds()
        report("PiP resize: before double-tap ${beforeSize.width()}x${beforeSize.height()}")
        tap(beforeSize.exactCenterX(), beforeSize.exactCenterY())
        SystemClock.sleep(80)
        tap(beforeSize.exactCenterX(), beforeSize.exactCenterY())
        waitFor { abs(bounds().width() - beforeSize.width()) > 20 }
        SystemClock.sleep(500)
        val beforePinch = bounds()
        report("PiP resize: after double-tap ${beforePinch.width()}x${beforePinch.height()}")
        pinch(beforePinch, if (beforePinch.width() < screenWidth * .45f) 1.5f else .6f)
        waitFor { abs(bounds().width() - beforePinch.width()) > 15 }
        SystemClock.sleep(500)
        report("PiP resize: after pinch ${bounds().width()}x${bounds().height()}")
        main { check(view()?.player === player && player.playWhenReady) }
        screenshot("$phase-resized")
        passed("$phase: drag, double-tap resize and two-finger pinch retain playback")

        systemAction("expand")
        waitFor { !activity.isInPictureInPictureMode && view()?.useController == true }
        main { check(view()?.player === player && player.playWhenReady) }
        passed("$phase: expand restores the full player and its playback state")

        if (phase == "real-movie") {
            main { view()?.showController() }
            click(label(R.string.player_display)); click("4:3"); click(label(R.string.dismiss))
            main { player.pause(); view()?.showController() }
            val fullscreen = mainValue {
                listOf(androidx.media3.ui.R.id.exo_fullscreen, androidx.media3.ui.R.id.exo_minimal_fullscreen)
                    .mapNotNull { view()?.findViewById<View>(it) }.first { it.isShown }.contentDescription.toString()
            }
            click(fullscreen)
            waitFor { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            val pausedPosition = position()
            main { view()?.showController() }
            click(label(R.string.player_pip))
            waitFor { activity.isInPictureInPictureMode }
            main { check(!player.playWhenReady) }
            systemAction("expand")
            waitFor { !activity.isInPictureInPictureMode && view()?.useController == true }
            main {
                check(view()?.player === player && !player.playWhenReady)
                check(abs(player.currentPosition - pausedPosition) < 500)
                check(activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                val content = checkNotNull(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame))
                check(abs(content.width.toFloat() / content.height - 4f / 3) < .04f)
            }
            shell("input keyevent KEYCODE_BACK")
            waitFor { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            main { player.play() }
            passed("$phase: paused landscape PiP/expand retains position, 4:3 display and fullscreen orientation")
            repeat(3) {
                shell("input keyevent KEYCODE_HOME")
                waitFor { activity.isInPictureInPictureMode }
                systemAction("expand")
                waitFor { !activity.isInPictureInPictureMode && view()?.player === player && player.isPlaying }
            }
            passed("$phase: repeated Home/expand cycles retain exactly the same playing instance")
        }

        main { view()?.showController() }
        click(label(R.string.player_pip))
        waitFor { activity.isInPictureInPictureMode }
        passed("$phase: the dedicated player button enters PiP")
        systemAction("dismiss")
        waitFor { !player.playWhenReady }
        SystemClock.sleep(1_000)
        reopen()
        SystemClock.sleep(1_000)
        main { check(view()?.player?.playWhenReady != true) }
        passed("$phase: closing PiP and reopening never restarts playback")
    }

    private fun lockChecks() {
        shell("input keyevent KEYCODE_HOME")
        waitFor { activity.isInPictureInPictureMode && player.isPlaying }
        shell("input keyevent KEYCODE_SLEEP")
        waitFor { !context.getSystemService(PowerManager::class.java).isInteractive && !player.playWhenReady }
        val paused = position()
        SystemClock.sleep(2_000)
        check(abs(position() - paused) < 500)
        passed("lock: turning the screen off pauses visible PiP immediately")
        shell("input keyevent KEYCODE_WAKEUP")
        report("HUMAN: unlock the phone normally; playback must remain paused")
        waitFor(180_000) { !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
        SystemClock.sleep(1_000)
        main { check(!player.playWhenReady && abs(player.currentPosition - paused) < 500) }
        passed("lock: waking and unlocking leaves playback paused at the saved position")
        if (mainValue { activity.isInPictureInPictureMode }) systemAction("expand") else reopen()
        waitFor { !activity.isInPictureInPictureMode && view()?.player === player }
        main { player.play() }
        waitFor { player.isPlaying }
        shell("input keyevent KEYCODE_SLEEP")
        waitFor { !context.getSystemService(PowerManager::class.java).isInteractive && !player.playWhenReady }
        shell("input keyevent KEYCODE_WAKEUP")
        report("HUMAN: unlock once more for the full-player lock test")
        waitFor(180_000) { !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
        main { check(!player.playWhenReady) }
        passed("lock: full-player lock/unlock also stays paused")
    }

    private fun reopen() {
        shell("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n ${context.packageName}/.MainActivity")
        waitFor(60_000) { activity.hasWindowFocus() && !activity.isInPictureInPictureMode }
    }

    private fun pipAction(label: String) {
        if (!visible(label)) { val b = bounds(); tap(b.exactCenterX(), b.exactCenterY()) }
        click(label)
    }

    private fun systemAction(kind: String) {
        // The mode callback precedes the system's bounds/orientation animation.
        // Tapping its intermediate bounds can hit the launcher instead of the PiP.
        SystemClock.sleep(800)
        if (nodes().none { it.isVisibleToUser && systemButton(it, kind) }) {
            val b = bounds(); tap(b.exactCenterX(), b.exactCenterY())
        }
        waitUi { nodes().any { it.isVisibleToUser && systemButton(it, kind) } }
        clickNode(nodes().first { it.isVisibleToUser && systemButton(it, kind) })
    }

    private fun systemButton(node: AccessibilityNodeInfo, kind: String): Boolean {
        val id = node.viewIdResourceName.orEmpty()
        return node.packageName?.toString() != context.packageName &&
            if (kind == "expand") id.contains("expand") else id.contains("dismiss") || id.contains("close")
    }

    private fun position() = mainValue { player.currentPosition }
    private fun bounds(): Rect = Rect().also { bounds ->
        checkNotNull(automation.windows.firstOrNull { it.id == appWindowId }).getBoundsInScreen(bounds)
    }
    private fun label(id: Int) = activity.getString(id)
    private fun visible(label: String) = nodes().any { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40 || result.size > 6000) return
            result += node
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        val active = automation.rootInActiveWindow
        visit(active, 0)
        automation.windows.filter { it.id != active?.windowId }.forEach { visit(it.root, 0) }
        return result
    }

    private fun click(text: String, timeout: Long = 15_000) {
        waitUi(timeout) { visible(text) }
        clickNode(nodes().first { it.isVisibleToUser && (it.text?.toString() == text || it.contentDescription?.toString() == text) })
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var button: AccessibilityNodeInfo? = node
        while (button != null && !button.isClickable) button = button.parent
        if (button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) {
            // Some system PiP image controls expose bounds but no accessibility click action.
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            check(!bounds.isEmpty)
            tap(bounds.exactCenterX(), bounds.exactCenterY())
        }
        SystemClock.sleep(350)
    }

    private fun view(root: View = activity.window.decorView): DisplayPlayerView? {
        if (root is DisplayPlayerView) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) view(root.getChildAt(i))?.let { return it }
        return null
    }

    private fun tap(x: Float, y: Float) {
        val start = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(35)
        }
    }
    private fun drag(x: Float, y: Float, toX: Float, toY: Float, duration: Long = 500) {
        val start = SystemClock.uptimeMillis()
        fun event(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                x + (toX - x) * fraction, y + (toY - y) * fraction, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..12) { SystemClock.sleep(duration / 12); event(MotionEvent.ACTION_MOVE, step / 12f) }
        event(MotionEvent.ACTION_UP, 1f)
    }

    private fun pinch(bounds: Rect, scale: Float) {
        val start = SystemClock.uptimeMillis()
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(action: Int, count: Int, progress: Float) {
            val distance = bounds.width() * .32f * (1 + (scale - 1) * progress)
            val coordinates = Array(2) { id -> MotionEvent.PointerCoords().apply {
                x = bounds.exactCenterX() + if (id == 0) -distance else distance
                y = bounds.exactCenterY(); pressure = 1f; size = .1f
            } }
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 1, 0f)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 0f)
        for (step in 1..18) { SystemClock.sleep(25); event(MotionEvent.ACTION_MOVE, 2, step / 18f) }
        event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, 1f)
        event(MotionEvent.ACTION_UP, 1, 1f)
    }

    private fun screenshot(name: String) {
        context.cacheDir.resolve("pip-$name.png").outputStream().use {
            automation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shell(command: String) { automation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).use { input -> input.readBytes() } } }
    private fun main(action: () -> Unit) = mainValue(action)
    private fun <T> mainValue(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
    private fun waitFor(timeout: Long = 15_000, condition: () -> Boolean) {
        waitUi(timeout) { mainValue(condition) }
    }
    private fun waitUi(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("PiP device condition timed out")
    }
}
