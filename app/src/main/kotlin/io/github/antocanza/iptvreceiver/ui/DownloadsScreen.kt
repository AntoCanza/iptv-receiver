package io.github.antocanza.iptvreceiver.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ReceiverApplication
import io.github.antocanza.iptvreceiver.data.DownloadEntry
import io.github.antocanza.iptvreceiver.data.DownloadFailure
import io.github.antocanza.iptvreceiver.data.DownloadStatus
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.ParentalPolicy
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.data.downloadExtension
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.playback.ExoPlaybackEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable
internal fun DownloadAction(target: WatchTarget, downloaded: Set<List<String>>, busy: Boolean,
    download: (WatchTarget) -> Unit, openDownloads: () -> Unit) {
    val saved = target.key in downloaded
    val supported = downloadExtension(target) != null
    val label = stringResource(if (saved) R.string.download_saved else R.string.download_action)
    val description = stringResource(R.string.action_for_title, label, target.episode?.title ?: target.item.title)
    if (supported) TextButton(onClick = { if (saved) openDownloads() else download(target) }, enabled = !busy,
        modifier = Modifier.semantics { contentDescription = description }) {
        Icon(painterResource(R.drawable.ic_download), contentDescription = null)
        Text(label, Modifier.padding(start = 8.dp))
    } else Text(stringResource(R.string.download_unsupported), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun DownloadsNavigation(container: ReceiverApplication, initial: String, session: Session?, back: () -> Unit) {
    val navigation = rememberNavController()
    val model: DownloadsViewModel = viewModel(key = "downloads", factory = viewModelFactory { initializer { DownloadsViewModel(container.downloads) } })
    val state by model.state.collectAsStateWithLifecycle()
    val entries by container.downloads.store.entries.collectAsStateWithLifecycle()
    val account by container.downloads.account.collectAsStateWithLifecycle()
    val policy by container.parentalControls.policy.collectAsStateWithLifecycle()
    val persistenceFailed by container.downloads.persistenceFailed.collectAsStateWithLifecycle()
    val current by navigation.currentBackStackEntryAsState()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, account) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh(); awaitCancellation() }
    }
    LaunchedEffect(policy.revision, account) {
        val id = current?.arguments?.getString("id")
        if (id != null && entries.none { it.id == id && it.account == account && it.allowed(policy) }) navigation.popBackStack()
    }
    BackHandler { if (!navigation.popBackStack()) back() }
    Column(Modifier.fillMaxSize()) {
        NavHost(navigation, startDestination = initial, modifier = Modifier.weight(1f)) {
            composable("downloads") {
                ScreenFrame(stringResource(R.string.downloads), back = back, action = {
                    IconButton(onClick = { navigation.navigate("download_settings") }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                }) {
                    DownloadsList(entries.filter { it.account == account }.sortedByDescending { it.createdAt }, policy, state,
                        persistenceFailed, model::refresh,
                        play = { id, restart -> navigation.navigate("download_player/$id/$restart") },
                        pause = { model.action { container.downloads.pause(it) } },
                        retry = { id -> model.action { container.downloads.resume(id, session?.connection, if (session != null) container.repository else null) } },
                        remove = { ids -> model.action { ids.forEach { container.downloads.remove(it) } } })
                }
            }
            composable("download_settings") {
                ScreenFrame(stringResource(R.string.downloads), back = { if (!navigation.popBackStack()) back() }) { DownloadSettings(model) }
            }
            composable("download_player/{id}/{restart}") { entry ->
                val id = checkNotNull(entry.arguments?.getString("id"))
                val restart = entry.arguments?.getString("restart") == "true"
                var loaded by remember(id) { mutableStateOf<Pair<DownloadEntry, PlaybackProgress?>?>(null) }
                var failed by remember(id) { mutableStateOf(false) }
                LaunchedEffect(id) {
                    try { loaded = container.downloads.playback(id) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { failed = true }
                }
                val data = loaded
                if (data == null) ScreenFrame(stringResource(R.string.downloads), back = { navigation.popBackStack() }) {
                    if (failed) Text(stringResource(R.string.download_action_error), Modifier.padding(24.dp)) else Loading()
                } else {
                    val download = data.first
                    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val activity = LocalActivity.current
                    val playback: PlaybackViewModel = viewModel(factory = viewModelFactory { initializer {
                        PlaybackViewModel(ExoPlaybackEngine(container, PlaybackSource("offline", download.title, false),
                            localUri = container.downloads.files.trustedUri(download.location)) {
                            val observed = System.nanoTime()
                            container.persistenceScope.launch { container.downloads.recordWatch(download, observed) }
                        }, landscape, if (!restart && data.second?.canResume == true) data.second!!.positionMs else 0,
                            onProgress = { progress ->
                                val observed = System.nanoTime()
                                container.persistenceScope.launch { container.downloads.saveProgress(download, progress, observed) }
                            })
                    } })
                    val fullscreen by playback.fullscreen.collectAsStateWithLifecycle()
                    LaunchedEffect(landscape) { playback.onOrientationChanged(landscape) }
                    fun fullscreen(enabled: Boolean) {
                        playback.setFullscreen(enabled)
                        activity?.requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    DisposableEffect(activity) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
                    BackHandler(fullscreen) { fullscreen(false) }
                    ScreenFrame(download.title, back = { navigation.popBackStack() }, fullscreen = fullscreen) {
                        Column {
                            if (persistenceFailed) Text(stringResource(R.string.watch_progress_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                            PlaybackScreen(playback, fullscreen, ::fullscreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsList(entries: List<DownloadEntry>, policy: ParentalPolicy, state: DownloadUiState,
    persistenceFailed: Boolean, refresh: () -> Unit, play: (String, Boolean) -> Unit,
    pause: (String) -> Unit, retry: (String) -> Unit, remove: (List<String>) -> Unit) {
    val context = LocalContext.current
    val busy = state.busy
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(entries.map { it.id }) { selected = selected.intersect(entries.map { it.id }.toSet()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failed) {
                Text(stringResource(R.string.download_action_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = refresh) { Text(stringResource(R.string.retry)) }
            }
            if (persistenceFailed) Text(stringResource(R.string.watch_progress_error), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.download_used, Formatter.formatShortFileSize(context,
                entries.filter { it.status != DownloadStatus.MISSING }.sumOf { it.bytes })), Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.download_offline_help), style = MaterialTheme.typography.bodySmall)
            if (entries.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { selected = if (selected.size == entries.size) emptySet() else entries.map { it.id }.toSet() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.download_select_all))
                }
                TextButton(onClick = { deleting = selected.toList() }, enabled = selected.isNotEmpty() && !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.download_remove_selected, selected.size))
                }
            }
        }
        if (entries.isEmpty()) item { Text(stringResource(R.string.download_empty), Modifier.padding(vertical = 24.dp)) }
        items(entries, key = { it.id }) { entry ->
            val allowed = entry.allowed(policy)
            val title = if (allowed) entry.title else stringResource(R.string.download_blocked)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().toggleable(entry.id in selected, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { selected = if (it) selected + entry.id else selected - entry.id }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(entry.id in selected, onCheckedChange = null, enabled = !busy)
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(entry.status.label()), style = MaterialTheme.typography.labelLarge)
                    Text(if (entry.total != null) stringResource(R.string.download_size,
                        Formatter.formatShortFileSize(context, entry.bytes), Formatter.formatShortFileSize(context, entry.total))
                        else Formatter.formatShortFileSize(context, entry.bytes), style = MaterialTheme.typography.bodySmall)
                    if (entry.active) {
                        if (entry.total?.let { it > 0 } == true) LinearProgressIndicator(
                            progress = { (entry.bytes.toFloat() / entry.total.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (entry.failure != DownloadFailure.NONE) Text(stringResource(entry.failure.label()), color = MaterialTheme.colorScheme.error)
                    if (allowed && entry.status == DownloadStatus.READY) {
                        val playLabel = stringResource(R.string.action_for_title, stringResource(R.string.play), title)
                        val restartLabel = stringResource(R.string.action_for_title, stringResource(R.string.restart_action), title)
                        Button(onClick = { play(entry.id, false) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = playLabel }) { Text(stringResource(R.string.play)) }
                        TextButton(onClick = { play(entry.id, true) }, enabled = !busy,
                            modifier = Modifier.semantics { contentDescription = restartLabel }) { Text(stringResource(R.string.restart_action)) }
                    }
                    if (entry.active) {
                        val pauseLabel = stringResource(R.string.action_for_title, stringResource(R.string.download_pause), title)
                        TextButton(onClick = { pause(entry.id) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = pauseLabel }) {
                            Text(stringResource(R.string.download_pause))
                        }
                    }
                    if (allowed && entry.status in setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED)) {
                        val retryLabel = stringResource(if (entry.status == DownloadStatus.PAUSED) R.string.resume_action else R.string.retry)
                        val description = stringResource(R.string.action_for_title, retryLabel, title)
                        TextButton(onClick = { retry(entry.id) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = description }) { Text(retryLabel) }
                    }
                    val removeLabel = stringResource(if (entry.active) R.string.download_cancel else R.string.download_remove)
                    val removeDescription = stringResource(R.string.action_for_title, removeLabel, title)
                    TextButton(onClick = { deleting = listOf(entry.id) }, enabled = !busy && entry.status != DownloadStatus.DELETING,
                        modifier = Modifier.semantics { contentDescription = removeDescription }) {
                        Text(removeLabel)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.download_remove_help), Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodySmall) }
    }
    deleting?.let { ids -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.download_remove)) }, text = { Text(stringResource(R.string.download_remove_confirm, ids.size)) },
        confirmButton = { TextButton(onClick = { deleting = null; remove(ids) }) { Text(stringResource(R.string.download_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
private fun DownloadSettings(model: DownloadsViewModel) {
    val folder by model.downloads.folder.collectAsStateWithLifecycle()
    val wifi by model.downloads.wifiOnly.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var available by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(folder) {
        available = if (folder == null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.getSystemService(android.os.storage.StorageManager::class.java).let {
                manager -> manager.getAllocatableBytes(manager.getUuidForPath(context.filesDir))
            } }.getOrNull()
        } else null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) model.action { model.downloads.setFolder(uri) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) Text(stringResource(R.string.download_action_error), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.download_folder), style = MaterialTheme.typography.titleMedium)
        Text(folder?.let { runCatching { DocumentsContract.getTreeDocumentId(it.toUri()).substringAfter(':') }.getOrNull() }
            ?: stringResource(R.string.download_internal))
        OutlinedButton(onClick = { picker.launch(null) }, enabled = !state.busy) { Text(stringResource(R.string.download_choose_folder)) }
        if (folder != null) TextButton(onClick = { model.action { model.downloads.setFolder(null) } }, enabled = !state.busy) {
            Text(stringResource(R.string.download_internal))
        }
        Text(stringResource(R.string.download_folder_help), style = MaterialTheme.typography.bodySmall)
        available?.let { Text(stringResource(R.string.download_free, Formatter.formatShortFileSize(context, it))) }
        Row(Modifier.fillMaxWidth().toggleable(wifi, enabled = !state.busy, role = Role.Checkbox,
            onValueChange = { model.action { model.downloads.setWifiOnly(it) } }), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(wifi, null, enabled = !state.busy)
            Text(stringResource(R.string.download_wifi))
        }
    }
}

private fun DownloadStatus.label(): Int = when (this) {
    DownloadStatus.QUEUED -> R.string.download_queued
    DownloadStatus.DOWNLOADING -> R.string.download_downloading
    DownloadStatus.PAUSED -> R.string.download_paused
    DownloadStatus.READY -> R.string.download_ready
    DownloadStatus.FAILED -> R.string.download_failed
    DownloadStatus.MISSING -> R.string.download_missing
    DownloadStatus.DELETING -> R.string.download_deleting
}

private fun DownloadFailure.label(): Int = when (this) {
    DownloadFailure.NONE, DownloadFailure.NETWORK -> R.string.download_network_error
    DownloadFailure.STORAGE -> R.string.download_storage_error
    DownloadFailure.UNSUPPORTED -> R.string.download_unsupported
    DownloadFailure.SIGN_IN -> R.string.download_sign_in
    DownloadFailure.BLOCKED -> R.string.download_blocked
}
