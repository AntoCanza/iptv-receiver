package io.github.antocanza.iptvreceiver

import android.app.Activity
import android.app.Instrumentation
import android.app.Application
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTrackNameProvider
import io.github.antocanza.iptvreceiver.model.*
import io.github.antocanza.iptvreceiver.playback.*
import io.github.antocanza.iptvreceiver.ui.*
import io.github.antocanza.iptvreceiver.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs

/** Explicit device checks. Real-provider mode requires -e real true and uses the device's saved connection. */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackDeviceChecks : Instrumentation() {
    @Volatile private lateinit var activity: MainActivity
    private var real = false
    private var normalUi = false
    private var displayOnly = false
    private var progressPhase: String? = null
    private var uxPhase: String? = null
    private var viewingPhase: String? = null
    private var screenSleep = false
    private var engine: PlaybackEngine? = null
    private var server: ServerSocket? = null
    private var completed = 0
    private var stage = "starting"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); real = arguments?.getString("real") == "true"; normalUi = arguments?.getString("normalUi") == "true"; displayOnly = arguments?.getString("displayOnly") == "true"; progressPhase = arguments?.getString("progressPhase"); uxPhase = arguments?.getString("uxPhase"); viewingPhase = arguments?.getString("viewingPhase"); screenSleep = arguments?.getString("screenSleep") == "true"; start() }
    override fun onStart() {
        val result = Bundle()
        try {
            if (viewingPhase == "data") {
                ViewingStoreChecks(targetContext, ::passed).run()
                result.putString("result", "PASS; completed=$completed; synthetic viewing storage")
                finish(Activity.RESULT_OK, result)
                return
            }
            val fixture = if (viewingPhase == "ui" || screenSleep) startMediaFixture() else null
            val intent = Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (fixture != null) intent.putExtra("store_preview", true).putExtra("preview_fixture_port", android.net.Uri.parse(fixture).port)
            activity = startActivitySync(intent) as MainActivity
            if (!screenSleep) onMain { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            if (screenSleep) screenSleepChecks(checkNotNull(fixture))
            else if (viewingPhase == "ui") viewingUiChecks()
            else if (uxPhase != null) { check(real); usabilityChecks(checkNotNull(uxPhase)) }
            else if (progressPhase != null) when (progressPhase) {
                "seed" -> { check(real); progressSeedChecks() }
                "verify" -> { check(real); progressNavigationChecks() }
                "reminder" -> reminderChecks()
                else -> error("Unknown device check")
            }
            else if (normalUi) { check(real); normalUiChecks() }
            else { if (real) realChecks(); subtitleChecks() }
            result.putString("result", "PASS; completed=$completed; real=$real")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            val root = uiAutomation.rootInActiveWindow
            status("active UI package=${root?.packageName}; children=${root?.childCount}")
            runCatching { java.io.FileOutputStream(java.io.File(targetContext.getExternalFilesDir(null), "device-check-failure.png")).use { uiAutomation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            runCatching { targetContext.cacheDir.resolve("ux-node-shape.txt").writeText(nodes().joinToString("\n") {
                "class=${it.className}; checkable=${it.isCheckable}; textName=${it.text?.toString()?.contains(label(R.string.remember_connection)) == true}; descName=${it.contentDescription?.toString()?.contains(label(R.string.remember_connection)) == true}; parentCheck=${it.parent?.isCheckable}"
            }) }
            // Never publish exception messages/causes, media URLs, content titles or provider responses.
            val location = error.stackTrace.firstOrNull { it.className.startsWith(javaClass.name) }
            result.putString("result", "FAIL; completed=$completed; type=${error.javaClass.simpleName}; method=${location?.methodName}; line=${location?.lineNumber}")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            runCatching { onMain { engine?.release(); engine = null; activity.requestedOrientation = -1 } }
            runCatching { server?.close() }
        }
    }
    private fun status(value: String) { val bundle = Bundle(); bundle.putString("check", value); sendStatus(0, bundle) }
    private fun passed(value: String) { completed++; status("PASS $value") }
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun waitUntil(timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) { var done = false; onMain { done = condition() }; if (done) return; SystemClock.sleep(100) }
        error("Device condition timed out")
    }
    private fun click(label: String, timeout: Long = 10_000, privateLabel: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            val nodes = nodes().filter { it.text?.toString()?.equals(label, true) == true || it.contentDescription?.toString()?.equals(label, true) == true }
                .sortedByDescending { it.isClickable || it.parent?.isClickable == true }
            for (node in nodes) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                SystemClock.sleep(150)
                node.refresh()
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                if (!node.isVisibleToUser || bounds.isEmpty) continue
                val time = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                val up = MotionEvent.obtain(time, time + 50, MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                try { sendPointerSync(down); sendPointerSync(up) } finally { down.recycle(); up.recycle() }
                SystemClock.sleep(350)
                return
            }
            SystemClock.sleep(200)
        }
        status("control missing: ${if (privateLabel) "provider item" else label}")
        error("Control not found")
    }
    private fun label(id: Int) = activity.getString(id)
    private fun nodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40 || found.size > 5000) return
            found += node
            for (i in 0 until node.childCount) visit(node.getChild(i), depth + 1)
        }
        visit(uiAutomation.rootInActiveWindow, 0)
        return found
    }
    private fun visible(label: String) = nodes().any { it.isVisibleToUser && (it.text?.toString()?.contains(label) == true || it.contentDescription?.toString() == label) }
    private fun attach(value: PlaybackEngine): PlaybackViewModel {
        onMain { engine?.release(); engine = value }
        lateinit var model: PlaybackViewModel
        onMain {
            model = PlaybackViewModel(value, false)
            activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { PlaybackScreen(model, false, {}) } } }
        }
        return model
    }
    private fun view(root: View = activity.window.decorView): DisplayPlayerView? {
        if (root is DisplayPlayerView) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) view(root.getChildAt(i))?.let { return it }
        return null
    }
    private fun screenSleepChecks(base: String) {
        lateinit var playback: ExoPlaybackEngine
        onMain { playback = ExoPlaybackEngine(targetContext, PlaybackSource("$base/fixture.wav", "Screen sleep fixture", false)) }
        val model = attach(playback)
        fun hasHold() = activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        waitUntil { playback.player.isPlaying && hasHold() }
        passed("active playback holds the screen awake")

        onMain { playback.player.pause() }
        waitUntil { !hasHold() }
        passed("pause releases the screen hold")
        onMain { playback.player.play() }
        waitUntil { playback.player.isPlaying && hasHold() }
        passed("resume restores the screen hold")

        onMain { playback.player.seekTo(playback.player.duration - 300) }
        waitUntil { playback.player.playbackState == Player.STATE_ENDED && !hasHold() }
        passed("natural completion releases the screen hold")
        onMain { playback.player.seekTo(0); playback.player.play() }
        waitUntil { playback.player.isPlaying && hasHold() }
        onMain { playback.player.stop() }
        waitUntil { !hasHold() }
        passed("stop releases the screen hold")

        onMain {
            playback.player.setMediaItem(MediaItem.fromUri("http://127.0.0.1:1/unavailable.wav"))
            model.retry()
        }
        waitUntil { playback.player.playerError != null && !hasHold() }
        passed("failed media releases the screen hold")
        onMain { playback.player.setMediaItem(MediaItem.fromUri("$base/fixture.wav")); model.retry() }
        waitUntil { playback.player.isPlaying && hasHold() }
        passed("retry can restore playback and the screen hold")
        onMain {
            activity.setContent { MaterialTheme { Box(Modifier.fillMaxSize()) } }
        }
        waitUntil { !hasHold() }
        passed("leaving the player clears the activity screen hold")
    }
    private fun waitUi(label: String, timeout: Long = 30_000) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) { if (visible(label)) return; SystemClock.sleep(200) }
        error("UI condition timed out")
    }
    private fun receiver(): ReceiverViewModel {
        lateinit var model: ReceiverViewModel
        onMain { model = ViewModelProvider.create(activity)["receiver", ReceiverViewModel::class] }
        waitUntil(60_000) { model.state.value.session != null && !model.history.value.busy }
        return model
    }
    private fun homeReady() {
        waitUi(label(R.string.tv_guide), 60_000)
        SystemClock.sleep(500)
        if (visible(label(R.string.subscription_reminder_title))) click(label(R.string.acknowledge))
    }
    private fun progressSeedChecks() = runBlocking {
        homeReady()
        val app = targetContext.applicationContext as ReceiverApplication
        val receiver = receiver()
        val session = checkNotNull(receiver.state.value.session)
        val targets = mutableListOf<WatchTarget>()
        for (type in listOf(ContentType.MOVIE, ContentType.SERIES)) {
            val items = app.repository.items(session.connection, type, null)
            val item = checkNotNull(items.firstOrNull { it.title.contains(if (type == ContentType.MOVIE) "interstellar" else "friends", true) })
            val target = if (type == ContentType.MOVIE) WatchTarget(app.repository.movie(session.connection, item).item)
                else WatchTarget(item, app.repository.series(session.connection, item).episodes.first { it.season >= 1 && it.extension.isNotEmpty() })
            val source = target.episode?.let { app.repository.episodeStream(session.connection, it) } ?: app.repository.stream(session.connection, target.item)
            val frames = AtomicInteger()
            val models = ViewModelStore()
            lateinit var exo: ExoPlaybackEngine
            onMain {
                receiver.preparePlayback(source, target)
                exo = ExoPlaybackEngine(targetContext, source) { receiver.recordWatch(target, session) }
                engine = exo
                exo.player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() }
                val model = ViewModelProvider.create(models, viewModelFactory { initializer {
                    PlaybackViewModel(exo, false, onProgress = { receiver.recordProgress(target, session, it, source) })
                } })[PlaybackViewModel::class]
                activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) { Box(Modifier.fillMaxSize().safeDrawingPadding()) { PlaybackScreen(model, false, {}) } } }
            }
            waitUntil(60_000) { frames.get() > 10 && exo.player.isPlaying }
            onMain { check(exo.player.duration > 930_000); exo.player.seekTo(900_000) }
            val prior = frames.get()
            waitUntil(60_000) { exo.player.currentPosition >= 900_000 && exo.player.isPlaying && frames.get() > prior + 10 }
            SystemClock.sleep(5_500)
            val checkpoint = checkNotNull(app.playbackProgressStore.load(session.connection)[target.key])
            check(checkpoint.positionMs >= 900_000 && checkpoint.canResume)
            onMain { exo.player.pause() }
            waitUntil { !exo.player.isPlaying }
            onMain { models.clear(); engine = null }
            val limit = SystemClock.elapsedRealtime() + 5_000
            while (app.playbackProgressStore.load(session.connection)[target.key]?.positionMs ?: 0 < checkpoint.positionMs && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(50)
            check(app.watchHistoryStore.load(session.connection).any { it.target.key == target.key })
            targets += target
            passed("$type real video and seek beyond 15 minutes; periodic and exit checkpoints persisted")
        }
        java.io.DataOutputStream(targetContext.filesDir.resolve("progress-check-targets.bin").outputStream()).use { output -> targets.forEach { target -> target.key.forEach(output::writeUTF) } }
    }
    private fun progressNavigationChecks() = runBlocking {
        homeReady()
        val app = targetContext.applicationContext as ReceiverApplication
        val receiver = receiver()
        val session = checkNotNull(receiver.state.value.session)
        val file = targetContext.filesDir.resolve("progress-check-targets.bin")
        val keys = java.io.DataInputStream(file.inputStream()).use { input -> List(2) { List(3) { input.readUTF() } } }
        val targets = keys.map { key -> receiver.history.value.entries.first { it.target.key == key }.target }
        val remembered = targets.associate { it.key to checkNotNull(app.playbackProgressStore.load(session.connection)[it.key]) }
        check(remembered.values.all { it.positionMs >= 900_000 && it.canResume })
        click(label(R.string.watch_history))
        for (target in targets) {
            click(target.item.title, privateLabel = true)
            waitUi(label(R.string.resume_title))
            val point = remembered.getValue(target.key)
            check(visible(formatWatchTime(point.positionMs)))
            click(label(R.string.resume_action))
            waitUntil(60_000) { view()?.player?.isPlaying == true && checkNotNull(view()?.player).currentPosition >= point.positionMs - 1_000 }
            val frames = AtomicInteger()
            onMain { (view()?.player as ExoPlayer).setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() } }
            waitUntil { frames.get() > 10 }
            onMain { check(checkNotNull(view()?.player).currentPosition < point.positionMs + 30_000); view()?.player?.pause() }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUi(label(R.string.watch_history))
            check(visible("15:"))
            passed("${target.item.type} normal history and Resume retain 15-minute position after process restart and render video")
            click(target.item.title, privateLabel = true)
            waitUi(label(R.string.resume_title))
            click(label(R.string.restart_action))
            waitUntil(60_000) { view()?.player?.isPlaying == true }
            onMain { check(checkNotNull(view()?.player).currentPosition < 10_000); view()?.player?.pause() }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUi(label(R.string.watch_history))
            val limit = SystemClock.elapsedRealtime() + 5_000
            while ((app.playbackProgressStore.load(session.connection)[target.key]?.positionMs ?: Long.MAX_VALUE) >= 10_000 && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(100)
            check(checkNotNull(app.playbackProgressStore.load(session.connection)[target.key]).positionMs < 10_000)
            passed("${target.item.type} Start over resets playback and durable point")
        }
        file.delete()
    }
    private fun reminderChecks() {
        val now = java.util.concurrent.atomic.AtomicReference(java.time.Instant.parse("2026-10-27T12:00:00Z"))
        val expiry = now.get().plusSeconds(45 * 86_400).epochSecond
        val directory = targetContext.cacheDir.resolve("reminder-device-fixture-${SystemClock.uptimeMillis()}")
        val connection = ProviderConnection("Synthetic", "https://fixture.example.test", "fixture", "synthetic")
        val repository = object : CatalogRepository {
            override suspend fun authenticate(connection: ProviderConnection) = Account("Active", expiry, listOf("ts"))
            override suspend fun categories(connection: ProviderConnection, type: ContentType) = emptyList<Category>()
            override suspend fun items(connection: ProviderConnection, type: ContentType, categoryId: String?) = emptyList<CatalogItem>()
            override suspend fun movie(connection: ProviderConnection, item: CatalogItem) = MovieDetails(item, "")
            override suspend fun series(connection: ProviderConnection, item: CatalogItem) = SeriesDetails(item.title, "", emptyList())
            override fun stream(connection: ProviderConnection, item: CatalogItem, liveFormat: String): PlaybackSource = error("No media in fixture")
            override fun episodeStream(connection: ProviderConnection, episode: Episode): PlaybackSource = error("No media in fixture")
        }
        val store = object : SavedConnectionStore {
            override fun load() = null
            override fun save(connection: ProviderConnection) {}
            override fun clear() {}
        }
        fun create(): ReceiverViewModel {
            lateinit var value: ReceiverViewModel
            onMain { value = ReceiverViewModel(repository, store, FileWatchHistoryStore(directory.resolve("history")),
                reminders = SubscriptionReminders(directory.resolve("ack"), now::get, { java.time.ZoneId.of("Europe/Rome") })) }
            waitUntil { !value.state.value.busy }
            onMain { value.connect(connection, false) }
            waitUntil { value.state.value.session != null }
            return value
        }
        var model = create()
        val allowed = mutableStateOf(true)
        fun mount() { onMain { val current = model; val session = checkNotNull(current.state.value.session)
            activity.setContent { MaterialTheme(colorScheme = darkColorScheme()) { SubscriptionReminder(current, session, allowed.value) } }
        } }
        mount(); waitUi(label(R.string.subscription_reminder_title))
        check(visible(activity.getString(R.string.subscription_days, 45)))
        click(label(R.string.acknowledge))
        SystemClock.sleep(500); check(!visible(label(R.string.subscription_reminder_title)))
        model = create(); mount()
        onMain { model.checkExpiryReminder() }
        SystemClock.sleep(700); check(!visible(label(R.string.subscription_reminder_title)))
        passed("expiry popup date and remaining days; OK dismisses and survives model recreation")
        onMain { allowed.value = false }
        now.set(now.get().plusSeconds(14 * 86_400))
        onMain { model.checkExpiryReminder() }
        waitUntil { model.expiryNotice.value != null }
        check(!visible(label(R.string.subscription_reminder_title)))
        onMain { allowed.value = true }
        waitUi(label(R.string.subscription_reminder_title)); click(label(R.string.acknowledge))
        SystemClock.sleep(500); check(!visible(label(R.string.subscription_reminder_title)))
        passed("next expiry interval becomes due; playback suppression defers popup until browsing; OK closes")
        // This directory contains only synthetic fixture data in our own cache directory.
        directory.deleteRecursively()
    }
    private fun usabilityChecks(phase: String) = runBlocking {
        val app = targetContext.applicationContext as ReceiverApplication
        val connection = checkNotNull(app.connectionStore.load())
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(value: Activity) { if (value is MainActivity) activity = value }
            override fun onActivityCreated(value: Activity, state: Bundle?) {}
            override fun onActivityStarted(value: Activity) {}
            override fun onActivityPaused(value: Activity) {}
            override fun onActivityStopped(value: Activity) {}
            override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
            override fun onActivityDestroyed(value: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            homeReady()
            when (phase) {
                "login" -> {
                    val receiver = receiver()
                    onMain { receiver.signOut() }
                    waitUntil { !receiver.state.value.busy && receiver.state.value.session == null }
                    lateinit var draft: LoginDraftViewModel
                    onMain {
                        draft = ViewModelProvider.create(activity)["login-draft", LoginDraftViewModel::class]
                        draft.server = connection.serverUrl
                        draft.username = connection.username
                        draft.password = connection.password
                    }
                    waitUi(label(R.string.remember_connection))
                    fun named(node: AccessibilityNodeInfo): Boolean =
                        node.text?.toString()?.contains(label(R.string.remember_connection)) == true ||
                        node.contentDescription?.toString()?.contains(label(R.string.remember_connection)) == true ||
                        (0 until node.childCount).any { node.getChild(it)?.let(::named) == true }
                    fun rememberOption() = nodes().first { it.isCheckable && named(it) }
                    SystemClock.sleep(600)
                    var remember = rememberOption()
                    check(remember.isEnabled && !remember.isChecked)
                    click(label(R.string.remember_connection))
                    SystemClock.sleep(400)
                    remember = rememberOption()
                    check(remember.isChecked)
                    passed("remember connection has a named checkbox; tapping the label toggles it")
                    val previous = activity
                    onMain { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    waitUntil { activity !== previous && activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    onMain {
                        val retained = ViewModelProvider.create(activity)["login-draft", LoginDraftViewModel::class]
                        check(retained === draft && retained.server == connection.serverUrl && retained.username == connection.username && retained.password == connection.password)
                    }
                    check(nodes().filter { it.className?.toString() == "android.widget.EditText" }.any { it.isPassword })
                    passed("real login draft survives rotation with password hidden")
                    onMain { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    waitUntil { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                    SystemClock.sleep(500)
                    // The same real account is used; no credentials or provider response leave this process.
                    click(label(R.string.connect))
                    waitUntil(60_000) { receiver.state.value.session != null }
                    waitUntil { draft.password.isEmpty() && draft.username.isEmpty() && draft.server.isEmpty() }
                    homeReady()
                    passed("real authentication succeeds with an empty optional connection name and clears the draft")
                }
                "live" -> {
                    val channel = app.repository.items(connection, ContentType.LIVE, null).first { it.title.equals("Rai 1 HD", true) }
                    click(label(R.string.live_tv))
                    waitUi(label(R.string.search_all_channels))
                    val search = nodes().first { it.className?.toString() == "android.widget.EditText" }
                    check(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Rai") }))
                    click(channel.title, 30_000, privateLabel = true)
                    waitUntil(60_000) { view()?.player?.playbackState == Player.STATE_READY && view()?.player?.isPlaying == true }
                    SystemClock.sleep(9_000)
                    onMain { status("live controller fully visible=" + view()?.isControllerFullyVisible) }
                    check(!visible(label(R.string.player_display)))
                    onMain { view()?.showController() }
                    waitUi(label(R.string.player_display))
                    click(label(R.string.tv_guide))
                    waitUi(label(R.string.guide_search))
                    passed("normal live navigation, automatic toolbar hiding and guide action")
                }
                "player" -> {
                    val item = app.repository.items(connection, ContentType.SERIES, null).first { it.title.equals("friends", true) }
                    val episode = app.repository.series(connection, item).episodes.first { it.season == 2 && it.number == 1 }
                    val source = app.repository.episodeStream(connection, episode)
                    val frames = AtomicInteger()
                    lateinit var exo: ExoPlaybackEngine
                    onMain { exo = ExoPlaybackEngine(targetContext, source); exo.player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() } }
                    val model = attach(exo)
                    waitUntil(60_000) { frames.get() > 10 && exo.player.isPlaying }
                    onMain { check(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.visibility == View.GONE); view()?.showController() }
                    click(label(R.string.player_audio))
                    val names = nodes().filter { it.isCheckable && it.text?.toString()?.contains("·") == true }.map { it.text.toString() }
                    check(names.size >= 2 && names.distinct().size == names.size)
                    passed("real episode renders and audio tracks have distinct numbered names")
                    sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    onMain { view()?.showController() }
                    click(label(R.string.player_speed)); click("1.25 ×")
                    onMain { check(exo.player.playbackParameters.speed == 1.25f); view()?.hideController() }
                    SystemClock.sleep(400)
                    check(!visible(label(R.string.player_display)) && !visible(label(R.string.player_audio)))
                    onMain { view()?.showController() }
                    waitUi(label(R.string.player_display))
                    check(visible(label(R.string.player_speed)))
                    passed("speed works; one toolbar follows controller visibility")
                    onMain { exo.player.setPlaybackSpeed(1f); view()?.showController() }
                    SystemClock.sleep(8_000)
                    check(!visible(label(R.string.player_display)))
                    onMain { view()?.showController() }
                    waitUi(label(R.string.player_display))
                    passed("toolbar also disappears after the automatic controller timeout")
                    displayChecks(model, frames)
                }
                else -> error("Unknown usability phase")
            }
        } finally {
            // A failed login check must not remove the user's previously remembered account.
            app.connectionStore.save(connection)
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }
    private fun normalUiChecks() = runBlocking {
        val app = targetContext.applicationContext as ReceiverApplication
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(value: Activity) { if (value is MainActivity) activity = value }
            override fun onActivityCreated(value: Activity, state: Bundle?) {}
            override fun onActivityStarted(value: Activity) {}
            override fun onActivityPaused(value: Activity) {}
            override fun onActivityStopped(value: Activity) {}
            override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
            override fun onActivityDestroyed(value: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            waitUi(label(R.string.tv_guide))
            val connection = checkNotNull(app.connectionStore.load())
            val repository = app.repository
            val channels = repository.items(connection, ContentType.LIVE, null)
            val channel = checkNotNull(channels.firstOrNull { it.title.contains("sky sport f1", true) && it.catchupDays > 0 })
            val categories = repository.categories(connection, ContentType.LIVE)
            val path = mutableListOf<Category>()
            var category = categories.firstOrNull { it.id == channel.categoryId }
            while (category != null && path.none { it.id == category?.id }) { path.add(0, category); category = categories.firstOrNull { it.id == category?.parentId } }
            click(label(R.string.tv_guide))
            for (entry in path) click(entry.name, privateLabel = true)
            val search = nodes().firstOrNull { it.className?.toString() == "android.widget.EditText" }
            if (search != null) search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "sky sport f1") })
            click(channel.title, privateLabel = true)
            click(label(R.string.guide_show_past))
            val programme = repository.guide(connection, channel).last { it.canReplay(channel, System.currentTimeMillis() / 1000) && it.endsAtSeconds < System.currentTimeMillis() / 1000 - 300 }
            val query = programme.title.take(12)
            val guideSearch = checkNotNull(nodes().firstOrNull { it.className?.toString() == "android.widget.EditText" })
            guideSearch.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query) })
            click(activity.getString(R.string.action_for_title, label(R.string.guide_replay), programme.title), 30_000)
            waitUntil(50_000) { view()?.player?.playbackState == Player.STATE_READY }
            lateinit var player: ExoPlayer
            val frames = AtomicInteger()
            onMain { player = view()?.player as ExoPlayer; player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() } }
            waitUntil { frames.get() > 10 }
            passed("normal guide navigation, programme search and catch-up button")
            click(label(R.string.player_display)); click("4:3"); click(label(R.string.dismiss))
            var pauseLabel = ""
            onMain { view()?.showController(); pauseLabel = checkNotNull(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)).contentDescription.toString() }
            click(pauseLabel)
            waitUntil { !player.playWhenReady }
            var position = 0L; var fullscreenLabel = ""
            onMain {
                position = player.currentPosition; view()?.showController()
                fullscreenLabel = listOf(androidx.media3.ui.R.id.exo_fullscreen, androidx.media3.ui.R.id.exo_minimal_fullscreen)
                    .mapNotNull { view()?.findViewById<View>(it) }.first { it.isShown }.contentDescription.toString()
            }
            click(fullscreenLabel)
            waitUntil { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && view()?.player === player && !player.playWhenReady }
            onMain {
                check(abs(player.currentPosition - position) < 600)
                val frame = checkNotNull(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame))
                check(abs(frame.width.toFloat() / frame.height - 4f / 3) < 0.03)
            }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUntil { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && view()?.player === player && !player.playWhenReady }
            var playLabel = ""
            onMain { check(abs(player.currentPosition - position) < 600); view()?.showController(); playLabel = checkNotNull(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)).contentDescription.toString() }
            val previous = frames.get(); click(playLabel); waitUntil { frames.get() > previous + 20 }
            passed("fullscreen rotation retains player, paused position and 4:3; resume renders")
            click(label(R.string.tv_guide))
            waitUi(activity.getString(R.string.action_for_title, label(R.string.guide_replay), programme.title))
            check(nodes().any { it.className?.toString() == "android.widget.EditText" && it.text?.toString()?.contains(query) == true })
            onMain { check(view() == null) }
            passed("return to guide restores past filter and search and releases player")
        } finally { app.unregisterActivityLifecycleCallbacks(callbacks) }
    }
    private fun displayChecks(model: PlaybackViewModel, frames: AtomicInteger) {
        val player = model.player
        var before = 0L
        onMain { before = player.currentPosition }
        click(label(R.string.player_display))
        for (scale in VideoScale.entries) {
            click(label(when (scale) { VideoScale.FIT -> R.string.player_fit; VideoScale.ZOOM -> R.string.player_zoom; VideoScale.STRETCH -> R.string.player_stretch }))
            for (aspect in VideoAspect.entries) {
                stage = "display $scale/$aspect"
                status("CHECK $stage")
                val previousFrames = frames.get()
                click(when (aspect) { VideoAspect.AUTO -> label(R.string.player_auto); VideoAspect.WIDE -> "16:9"; VideoAspect.CLASSIC -> "4:3"; VideoAspect.CINEMA -> "21:9" })
                try { waitUntil {
                    val frame = view()?.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
                    frames.get() > previousFrames + 2 && model.display.value == VideoDisplay(scale, aspect) &&
                        (aspect.ratio == null || scale == VideoScale.STRETCH || (frame != null && frame.height > 0 && abs(frame.width.toFloat() / frame.height - aspect.ratio) < 0.03))
                } } catch (error: Throwable) {
                    onMain { val f = view()?.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
                        status("display geometry=${f?.width}x${f?.height}; frameDelta=${frames.get() - previousFrames}; selection=${model.display.value}") }
                    throw error
                }
                onMain {
                    val frame = checkNotNull(view()?.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame))
                    if (aspect.ratio != null && scale != VideoScale.STRETCH && frame.height > 0) check(abs(frame.width.toFloat() / frame.height - aspect.ratio) < 0.03)
                    check(player.playerError == null)
                }
            }
        }
        click(label(R.string.dismiss))
        onMain { check(player.currentPosition >= before); model.setDisplay(VideoDisplay()) }
        onMain { view()?.showController() }
        click(label(R.string.player_info))
        check(visible(label(R.string.player_video)))
        check(visible(label(R.string.player_codec)))
        click(label(R.string.dismiss))
        passed("all 12 display combinations and stream information")
    }
    private fun realChecks() = runBlocking {
        val app = targetContext.applicationContext as ReceiverApplication
        val connection = checkNotNull(app.connectionStore.load())
        val repository = app.repository
        repository.authenticate(connection)
        val types = listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)
        var successful = 0
        var archiveAvailable = false
        var archiveTested = false
        for (type in types) {
            val items = repository.items(connection, type, null)
            val preferences = when (type) { ContentType.LIVE -> listOf("sky sport f1", "rai 1", "sky tg24"); ContentType.MOVIE -> listOf("interstellar", "avatar", "frozen"); ContentType.SERIES -> listOf("friends", "breaking bad", "lost", "dexter") }
            val candidates = preferences.mapNotNull { key -> items.firstOrNull { it.title.contains(key, ignoreCase = true) } }.distinctBy { it.id }
            var passed = 0
            for ((index, item) in candidates.withIndex()) {
                if (passed >= 2) break
                try {
                    stage = "resolve $type candidate=${index + 1}"
                    val source = when (type) {
                        ContentType.LIVE -> repository.stream(connection, item, "m3u8")
                        ContentType.MOVIE -> repository.stream(connection, repository.movie(connection, item).item)
                        ContentType.SERIES -> repository.episodeStream(connection, repository.series(connection, item).episodes.first { it.season >= 1 && it.extension.isNotEmpty() })
                    }
                    val frames = AtomicInteger()
                    lateinit var exo: ExoPlaybackEngine
                    onMain { exo = ExoPlaybackEngine(targetContext, source); exo.player.setVideoFrameMetadataListener { _, _, _, _ -> frames.incrementAndGet() } }
                    val model = attach(exo)
                    stage = "initial video $type candidate=${index + 1}"
                    status("START $type candidate=${index + 1}")
                    waitUntil(50_000) { frames.get() > 10 || exo.player.playerError != null }
                    onMain { check(exo.player.playerError == null && frames.get() > 10) }
                    stage = "display $type candidate=${index + 1}"
                    if (type == ContentType.LIVE && passed == 0) displayChecks(model, frames)
                    if (displayOnly) return@runBlocking
                    stage = "observe $type candidate=${index + 1}"
                    val begin = frames.get()
                    SystemClock.sleep(if (type == ContentType.LIVE) 60_000 else 10_000)
                    onMain { check(exo.player.playerError == null && frames.get() > begin + 20); exo.player.pause() }
                    var paused = 0L
                    onMain { paused = exo.player.currentPosition }
                    SystemClock.sleep(1200)
                    val resumeFrames = frames.get()
                    onMain { check(abs(exo.player.currentPosition - paused) < 600); exo.player.play() }
                    waitUntil { frames.get() > resumeFrames + 10 }
                    if (type != ContentType.LIVE) {
                        onMain { exo.player.seekTo(60_000) }
                        waitUntil(30_000) { exo.player.currentPosition >= 57_000 && exo.player.isPlaying }
                    }
                    passed++; successful++
                    passed("$type candidate=${index + 1}; decodedFrames=${frames.get()}; pause/resume; seek=${type != ContentType.LIVE}")
                    onMain { exo.release(); engine = null }
                    if (type == ContentType.LIVE && !archiveTested) {
                        val programmes = repository.guide(connection, item)
                        val archive = programmes.lastOrNull { it.canReplay(item, System.currentTimeMillis() / 1000) }
                        status("guide entries=${programmes.size}; archiveAvailable=${archive != null}")
                        if (archive != null) {
                            archiveAvailable = true
                            val archiveSource = repository.catchupStream(connection, item, archive)
                            lateinit var archiveEngine: ExoPlaybackEngine
                            val archiveFrames = AtomicInteger()
                            onMain { archiveEngine = ExoPlaybackEngine(targetContext, archiveSource); archiveEngine.player.setVideoFrameMetadataListener { _, _, _, _ -> archiveFrames.incrementAndGet() } }
                            attach(archiveEngine)
                            waitUntil(50_000) { archiveFrames.get() > 10 || archiveEngine.player.playerError != null }
                            onMain { check(archiveEngine.player.playerError == null); check(archiveEngine.player.duration <= checkNotNull(archiveSource.durationMs) + 1000); archiveEngine.player.seekTo(15_000) }
                            waitUntil(30_000) { archiveEngine.player.currentPosition >= 12_000 && archiveEngine.player.isPlaying }
                            SystemClock.sleep(10_000)
                            onMain { check(archiveEngine.player.playerError == null && archiveFrames.get() > 40); archiveEngine.release(); engine = null }
                            passed("catch-up decoded frames, bounded duration and seek")
                            archiveTested = true
                        }
                    }
                } catch (error: Throwable) {
                    val location = error.stackTrace.firstOrNull { it.className.startsWith(javaClass.name) }
                    var code: Int? = null
                    var state = 0; var playing = false; var ready = false
                    onMain { code = engine?.player?.playerError?.errorCode; state = engine?.player?.playbackState ?: 0; playing = engine?.player?.isPlaying == true; ready = engine?.player?.playWhenReady == true }
                    status("UNAVAILABLE $stage; type=${error.javaClass.simpleName}; method=${location?.methodName}; line=${location?.lineNumber}; playerCode=$code; state=$state; playing=$playing; playWhenReady=$ready")
                    onMain { engine?.release(); engine = null }
                    if (displayOnly) throw error
                }
            }
            check(passed >= 1)
        }
        check(successful >= 5)
        check(!archiveAvailable || archiveTested)
    }
    private fun startMediaFixture(): String {
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")); server = socket
        val pcm = ByteArray(120 * 8000 * 2)
        val wav = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(8000); putInt(16000); putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
        thread(isDaemon = true, name = "subtitle-fixture") {
            while (!socket.isClosed) runCatching {
                socket.accept().use { client ->
                    val input = client.getInputStream().bufferedReader()
                    val path = input.readLine().orEmpty().split(' ').getOrNull(1).orEmpty()
                    var range: String? = null
                    while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break; if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim() }
                    val data = if (path.contains(".vtt")) "WEBVTT\n\n00:00:00.000 --> 00:01:59.000\n${if (path.contains("it.vtt")) "ITALIANO" else "ENGLISH"}\n".toByteArray() else wav
                    val start = range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()?.coerceIn(0, data.lastIndex) ?: 0
                    val headers = "HTTP/1.1 ${if (range == null) "200 OK" else "206 Partial Content"}\r\nContent-Type: ${if (path.contains(".vtt")) "text/vtt" else "audio/wav"}\r\nContent-Length: ${data.size - start}\r\nAccept-Ranges: bytes\r\n${if (range != null) "Content-Range: bytes $start-${data.lastIndex}/${data.size}\r\n" else ""}Connection: close\r\n\r\n"
                    client.getOutputStream().apply { write(headers.toByteArray()); write(data, start, data.size - start); flush() }
                }
            }
        }
        return "http://127.0.0.1:${socket.localPort}"
    }
    private fun subtitleChecks() {
        status("subtitle fixture starting")
        val base = startMediaFixture()
        lateinit var player: ExoPlayer
        onMain { player = ExoPlayer.Builder(targetContext).build(); player.volume = 0f; player.setMediaItem(MediaItem.fromUri("$base/fixture.wav")) }
        val wrapper = object : PlaybackEngine {
            override val player = player
            override val recoveryState = MutableStateFlow(PlaybackRecoveryState.NONE)
            override val keepScreenOn = MutableStateFlow(false)
            override fun prepareAndPlay() { player.prepare(); player.play() }
            override fun pause() = player.pause()
            override fun release() = player.release()
        }
        attach(wrapper)
        waitUntil { player.playbackState == Player.STATE_READY || player.playerError != null }
        onMain { check(player.playerError == null) }
        status("subtitle fixture ready without text")
        check(!visible(label(R.string.player_subtitles)))
        onMain {
            val configs = listOf("en", "it").map { language -> MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse("$base/$language.vtt"))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(language).setSelectionFlags(if (language == "it") C.SELECTION_FLAG_FORCED else 0).build() }
            player.setMediaItem(MediaItem.Builder().setUri("$base/fixture.wav").setSubtitleConfigurations(configs).build()); player.prepare(); player.play()
        }
        waitUntil { player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT } == 2 }
        status("subtitle fixture has two text groups")
        SystemClock.sleep(800)
        for ((language, cue) in listOf("it" to "ITALIANO", "en" to "ENGLISH")) {
            status("subtitle selection language=$language")
            click(label(R.string.player_subtitles))
            val name = DefaultTrackNameProvider(activity.resources).getTrackName(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage(language).build())
            click(name); click(activity.getString(android.R.string.ok))
            waitUntil { player.currentCues.cues.any { it.text.toString().contains(cue) } }
            click(label(R.string.player_subtitles)); click(activity.getString(androidx.media3.ui.R.string.exo_track_selection_none)); click(activity.getString(android.R.string.ok))
            waitUntil { C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes && player.currentCues.cues.isEmpty() }
            passed("subtitle language=$language, rendered cue and full Off including forced captions")
        }
        onMain { player.setMediaItem(MediaItem.fromUri("$base/fixture.wav")); player.prepare(); player.play() }
        waitUntil { player.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT } }
        SystemClock.sleep(500)
        check(!visible(label(R.string.player_subtitles)))
        passed("subtitle control hidden when tracks disappear")
        onMain { player.release(); engine = null }; server?.close()
    }

    private fun viewingUiChecks() = runBlocking {
        homeReady()
        val app = targetContext.applicationContext as ReceiverApplication
        lateinit var model: ReceiverViewModel
        onMain { model = ViewModelProvider.create(activity)["store-preview", ReceiverViewModel::class] }
        waitUntil { model.state.value.session != null }
        val connection = checkNotNull(model.state.value.session).connection
        val catalog = checkNotNull(StorePreview.create(activity.intent)).first
        val title = catalog.items(connection, ContentType.MOVIE, null).first()
        val show = catalog.items(connection, ContentType.SERIES, null).first()
        val episodes = catalog.series(connection, show).episodes
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(value: Activity) {
                if (value is MainActivity) {
                    activity = value
                    value.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            override fun onActivityCreated(value: Activity, state: Bundle?) {}
            override fun onActivityStarted(value: Activity) {}
            override fun onActivityPaused(value: Activity) {}
            override fun onActivityStopped(value: Activity) {}
            override fun onActivitySaveInstanceState(value: Activity, state: Bundle) {}
            override fun onActivityDestroyed(value: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        fun scroll(text: String) {
            repeat(18) {
                if (visible(text)) return
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(250)
            }
            error("Fixture control not reachable")
        }
        fun top() {
            repeat(16) {
                val node = nodes().firstOrNull { it.isScrollable } ?: return
                if (!node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return
                SystemClock.sleep(120)
            }
        }
        fun activateNamedAction(description: String) {
            repeat(24) {
                var action = nodes().firstOrNull { it.contentDescription?.toString()?.contains(description) == true }
                while (action != null && !action.isClickable) action = action.parent
                val button = action
                if (button != null) {
                    button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    SystemClock.sleep(200)
                    button.refresh()
                    if (button.isVisibleToUser && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        SystemClock.sleep(350)
                        return
                    }
                }
                nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(200)
            }
            error("Named fixture action not reachable")
        }
        fun screenshot(name: String) {
            java.io.FileOutputStream(java.io.File(targetContext.getExternalFilesDir(null), "watch-progress-$name.png")).use {
                uiAutomation.takeScreenshot()?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        try {
            app.watchHistoryStore.clear(connection)
            app.playbackProgressStore.clear(connection)
            app.playbackProgressStore.save(connection, WatchTarget(title), PlaybackProgress(60_000, 120_000))
            episodes.take(2).forEach { app.playbackProgressStore.setWatched(connection, WatchTarget(show, it), true) }
            app.playbackProgressStore.save(connection, WatchTarget(show, episodes[2]), PlaybackProgress(30_000, 120_000, lastPlayedAtMillis = System.currentTimeMillis()))
            click(label(R.string.movies)); click("Featured movies"); click(title.title)
            waitUi(activity.getString(R.string.watch_resume_at, "1:00"))
            click(activity.getString(R.string.watch_resume_at, "1:00"))
            waitUntil { view()?.player?.isPlaying == true && checkNotNull(view()?.player).currentPosition in 59_000..80_000 }
            onMain { view()?.player?.pause() }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUi(title.title)
            scroll(label(R.string.restart_action)); click(label(R.string.restart_action))
            waitUntil { view()?.player?.isPlaying == true }
            onMain { check(checkNotNull(view()?.player).currentPosition < 8_000); view()?.player?.pause() }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUi(title.title)
            scroll(label(R.string.watch_mark_watched)); click(label(R.string.watch_mark_watched))
            waitUi(label(R.string.watch_again))
            screenshot("movie")
            passed("movie detail resumes directly, Start over seeks to zero and manual watched status updates")

            val previous = activity
            onMain { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            waitUntil { activity !== previous && activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            waitUi(label(R.string.watch_again))
            onMain { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitUntil { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            waitUi(label(R.string.watch_again))
            check(app.playbackProgressStore.read(connection, WatchTarget(title))?.watched == true)
            passed("watched status survives landscape and portrait recreation")

            repeat(3) { sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); SystemClock.sleep(300) }
            homeReady()
            click(label(R.string.series)); click("Featured series"); click(show.title)
            waitUi(activity.getString(R.string.watch_continue_episode, 1, 3))
            scroll(activity.getString(R.string.watch_resume_at, "0:30")); click(activity.getString(R.string.watch_resume_at, "0:30"))
            waitUntil { view()?.player?.isPlaying == true && checkNotNull(view()?.player).currentPosition in 29_000..50_000 }
            onMain { view()?.player?.pause() }
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitUi(show.title)
            val markEpisode = activity.getString(R.string.action_for_title, label(R.string.watch_mark_watched), episodes[2].title)
            activateNamedAction(markEpisode)
            waitUntil { app.playbackProgressStore.read(connection, WatchTarget(show, episodes[2]))?.watched == true }
            top()
            waitUi(activity.getString(R.string.watch_continue_episode, 1, 4))
            scroll(activity.getString(R.string.watch_season_count, 3, 4))
            waitUi(activity.getString(R.string.watch_season_count, 3, 4))
            screenshot("series")
            passed("series continues episode three, per-episode marking updates the season count and advances the Continue action")

            repeat(3) { sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); SystemClock.sleep(300) }
            homeReady(); click(label(R.string.settings)); click(label(R.string.watch_storage_title))
            click(label(R.string.watch_clear)); click(label(R.string.cancel))
            check(app.playbackProgressStore.read(connection, WatchTarget(title))?.watched == true)
            click(label(R.string.watch_clear)); click(label(R.string.watch_clear))
            waitUi(label(R.string.watch_cleared))
            check(app.playbackProgressStore.load(connection).isEmpty())
            check(app.watchHistoryStore.load(connection).isEmpty())
            passed("settings clear requires confirmation; cancellation preserves data and confirmation clears only the fixture account")
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            onMain { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
