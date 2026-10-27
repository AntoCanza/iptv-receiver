package io.github.antocanza.iptvreceiver.ui

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTrackNameProvider
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import io.github.antocanza.iptvreceiver.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
class DisplayPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PlayerView(context, attrs) {
    private var display: VideoDisplay? = null
    fun setVideoDisplay(value: VideoDisplay) {
        if (display == value) return
        display = value
        resizeMode = when (value.scale) {
            VideoScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoScale.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            VideoScale.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
        val size = player?.videoSize
        val ratio = if (size != null && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 0f
        onContentAspectRatioChanged(findViewById(androidx.media3.ui.R.id.exo_content_frame), ratio)
    }
    override fun onContentAspectRatioChanged(contentFrame: AspectRatioFrameLayout?, aspectRatio: Float) {
        // The override also applies when a live programme or adaptive stream changes size.
        super.onContentAspectRatioChanged(contentFrame, display?.aspect?.ratio ?: aspectRatio)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PlaybackTools(model: PlaybackViewModel, onGuide: (() -> Unit)?, visible: Boolean = true) {
    val player = model.player
    val display by model.display.collectAsStateWithLifecycle()
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var dialog by remember(player) { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(value: Tracks) {
                tracks = value
                if (value.groups.none { it.type == C.TRACK_TYPE_TEXT } && dialog == "subtitles") dialog = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (visible) FlowRow(Modifier.zIndex(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout).padding(8.dp),
        horizontalArrangement = Arrangement.End) {
        ToolIcon(R.drawable.ic_display, stringResource(R.string.player_display)) { dialog = "display" }
        if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }) {
            ToolIcon(R.drawable.ic_subtitles, stringResource(R.string.player_subtitles), tracks.isTypeSelected(C.TRACK_TYPE_TEXT)) { dialog = "subtitles" }
        }
        if (tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.sumOf { it.length } > 1) {
            ToolIcon(R.drawable.ic_audio, stringResource(R.string.player_audio)) { dialog = "audio" }
        }
        ToolIcon(R.drawable.ic_clock, stringResource(R.string.player_speed)) { dialog = "speed" }
        ToolIcon(R.drawable.ic_info, stringResource(R.string.player_info)) { dialog = "info" }
        if (onGuide != null) ToolIcon(R.drawable.ic_calendar, stringResource(R.string.tv_guide), onClick = onGuide)
    }
    when (dialog) {
        "display" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.player_display)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.player_scale), style = MaterialTheme.typography.titleSmall)
                    VideoScale.entries.forEach { scale ->
                        Choice(stringResource(when (scale) { VideoScale.FIT -> R.string.player_fit; VideoScale.ZOOM -> R.string.player_zoom; VideoScale.STRETCH -> R.string.player_stretch }),
                            display.scale == scale) { model.setDisplay(display.copy(scale = scale)) }
                    }
                    Text(stringResource(R.string.player_aspect), style = MaterialTheme.typography.titleSmall)
                    VideoAspect.entries.forEach { aspect ->
                        Choice(when (aspect) { VideoAspect.AUTO -> stringResource(R.string.player_auto); VideoAspect.WIDE -> "16:9"; VideoAspect.CLASSIC -> "4:3"; VideoAspect.CINEMA -> "21:9" },
                            display.aspect == aspect) { model.setDisplay(display.copy(aspect = aspect)) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dismiss)) } })
        "subtitles", "audio" -> {
            val context = LocalContext.current
            val resources = LocalResources.current
            val type = if (dialog == "subtitles") C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
            val title = stringResource(if (type == C.TRACK_TYPE_TEXT) R.string.player_subtitles else R.string.player_audio)
            DisposableEffect(player, context, resources, title, type) {
                val names = DefaultTrackNameProvider(resources)
                val labels = java.util.IdentityHashMap<Format, String>()
                val formats = tracks.groups.filter { it.type == type }.flatMap { group -> (0 until group.length).map(group::getTrackFormat) }
                formats.forEachIndexed { index, format -> labels[format] = resources.getString(R.string.player_track_number, index + 1, names.getTrackName(format)) }
                val selector = TrackSelectionDialogBuilder(context, title, player, type)
                    .setTrackNameProvider { format -> labels[format] ?: names.getTrackName(format) }
                    .setShowDisableOption(type == C.TRACK_TYPE_TEXT)
                    .setIsDisabled(type in player.trackSelectionParameters.disabledTrackTypes)
                    .build()
                selector.setOnDismissListener { dialog = null }
                selector.show()
                onDispose { selector.setOnDismissListener(null); selector.dismiss() }
            }
        }
        "speed" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.player_speed)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                for (speed in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) Choice("$speed ×", player.playbackParameters.speed == speed) {
                    player.setPlaybackSpeed(speed); dialog = null
                }
            } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dismiss)) } })
        "info" -> StreamInfo(player) { dialog = null }
    }
}

@Composable
private fun ToolIcon(icon: Int, label: String, selected: Boolean? = null, onClick: () -> Unit) {
    ActionIcon(icon, label, modifier = Modifier.padding(2.dp), selected = selected,
        stateLabel = selected?.let { stringResource(if (it) R.string.player_yes else R.string.player_no) }, onVideo = true, onClick = onClick)
}

@Composable
private fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(title, Modifier.padding(start = 12.dp))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StreamInfo(player: Player, close: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) { while (isActive) { delay(1000); tick++ } }
    // Read on the player's main thread, including renderer format changes within one adaptive group.
    val snapshot = remember(player, tick) {
        val exo = player as? ExoPlayer
        Triple(exo?.videoFormat, exo?.audioFormat, player.currentTracks)
    }
    val unknown = stringResource(R.string.player_unknown)
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    val names = remember(resources) { DefaultTrackNameProvider(resources) }
    fun number(value: Int?) = value?.takeIf { it > 0 }?.toString() ?: unknown
    fun rate(value: Int?) = value?.takeIf { it > 0 }?.let { "${it / 1000} kb/s" } ?: unknown
    AlertDialog(onDismissRequest = close, title = { Text(stringResource(R.string.player_info)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.player_video), style = MaterialTheme.typography.titleMedium)
            val video = snapshot.first
            Info(R.string.player_codec, video?.codecs ?: video?.sampleMimeType ?: unknown)
            Info(R.string.player_resolution, if (video != null && video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else unknown)
            val size = player.videoSize
            Info(R.string.player_aspect, if (size.height > 0) String.format(locale, "%.3f:1", size.width * size.pixelWidthHeightRatio / size.height) else unknown)
            Info(R.string.player_fps, video?.frameRate?.takeIf { it > 0 }?.let { String.format(locale, "%.3f fps", it) } ?: unknown)
            Info(R.string.player_bitrate, rate(video?.averageBitrate))
            val color = video?.colorInfo
            val colorName = when (color?.colorSpace) { C.COLOR_SPACE_BT709 -> "BT.709"; C.COLOR_SPACE_BT2020 -> "BT.2020"; C.COLOR_SPACE_BT601 -> "BT.601"; else -> unknown }
            val transfer = when (color?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ"; C.COLOR_TRANSFER_HLG -> "HLG"; C.COLOR_TRANSFER_SDR -> "SDR"; else -> unknown }
            Info(R.string.player_color, "$colorName · $transfer · ${color?.lumaBitdepth?.takeIf { it > 0 }?.let { "$it-bit" } ?: unknown}")
            val audio = snapshot.second
            Text(stringResource(R.string.player_audio), style = MaterialTheme.typography.titleMedium)
            Info(R.string.player_codec, audio?.codecs ?: audio?.sampleMimeType ?: unknown)
            Info(R.string.player_channels, number(audio?.channelCount))
            Info(R.string.player_sample_rate, audio?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" } ?: unknown)
            Info(R.string.language, audio?.language?.takeUnless { it == "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) } ?: unknown)
            Info(R.string.player_bitrate, rate(audio?.averageBitrate))
            snapshot.third.groups.filter { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                for (i in 0 until group.length) {
                    Text((if (group.isTrackSelected(i)) "✓ " else "") + names.getTrackName(group.getTrackFormat(i)))
                }
            }
            Info(R.string.player_duration, player.duration.takeIf { it > 0 }?.let { "${it / 60000}:${(it / 1000 % 60).toString().padStart(2, '0')}" } ?: unknown)
            Info(R.string.player_seekable, stringResource(if (player.isCurrentMediaItemSeekable) R.string.player_yes else R.string.player_no))
            Text(stringResource(if (player.isCurrentMediaItemLive) R.string.live_tv else R.string.player_on_demand))
        }
    }, confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.dismiss)) } })
}

@Composable
private fun Info(label: Int, value: String) { Text("${stringResource(label)}: $value") }
