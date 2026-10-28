package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ReceiverApplication
import io.github.antocanza.iptvreceiver.data.ParentalCatalogRepository
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.preferredLiveFormat
import io.github.antocanza.iptvreceiver.playback.ExoPlaybackEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation

@Composable
fun LoginScreen(state: LoginState, connect: (ProviderConnection, Boolean) -> Unit, draft: LoginDraftViewModel = viewModel(),
    openDownloads: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf("") }
    // Credentials stay out of Android's saved-state bundles.
    val server = draft.server
    val username = draft.username
    val password = draft.password
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberConnection by rememberSaveable { mutableStateOf(false) }
    val defaultConnectionName = stringResource(R.string.default_connection_name)
    ScreenFrame(stringResource(R.string.app_name), showLanguage = true) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                openDownloads?.let { action -> NavigationCard(R.drawable.ic_download, stringResource(R.string.downloads), action) }
                Text(stringResource(R.string.connection_help))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.connection_name_optional)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(server, { draft.server = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_server), contentDescription = null) },
                    label = { Text(stringResource(R.string.server_address)) }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                OutlinedTextField(username, { draft.username = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                    label = { Text(stringResource(R.string.username)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(password, { draft.password = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) }, enabled = !state.busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.busy) {
                            Icon(painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Row(Modifier.fillMaxWidth().toggleable(value = rememberConnection, enabled = !state.busy, role = Role.Checkbox, onValueChange = { rememberConnection = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberConnection, null, enabled = !state.busy)
                    Text(stringResource(R.string.remember_connection))
                }
                state.error?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
                Button(onClick = { connect(ProviderConnection(name.trim().ifBlank { defaultConnectionName },
                    server.trim(), username.trim(), password), rememberConnection) },
                    enabled = !state.busy && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.connect))
                }
            }
        }
    }
}

@Composable
fun ReceiverNavigation(repository: CatalogRepository, session: Session, model: ReceiverViewModel, openDownloads: (Boolean) -> Unit = {}) {
    val navigation = rememberNavController()
    val container = LocalContext.current.applicationContext as ReceiverApplication
    val policy by container.parentalControls.policy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var resolvingPlayback by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<FailureReason?>(null) }
    var resumeRequest by remember { mutableStateOf<ResumeRequest?>(null) }
    val currentEntry by navigation.currentBackStackEntryAsState()
    SubscriptionReminder(model, session, allowed = currentEntry?.destination?.route != "player" && resumeRequest == null && !resolvingPlayback)
    suspend fun openPlayback(target: WatchTarget, resolve: () -> PlaybackSource, position: Long, restart: Boolean = false) {
        if (repository is ParentalCatalogRepository) repository.authorizeEpisode(session.connection, target)
        val source = resolve()
        model.preparePlayback(source, target, position)
        if (restart) model.restartProgress(target, session)
        navigation.navigate("player") { launchSingleTop = true }
    }
    fun launchPlayback(target: WatchTarget, resolve: () -> PlaybackSource, position: Long, restart: Boolean = false) {
        if (resolvingPlayback) return
        resolvingPlayback = true
        scope.launch {
            try {
                openPlayback(target, resolve, position, restart)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { playbackError = safeMessage(error) }
            finally { resolvingPlayback = false }
        }
    }
    fun startPlayback(target: WatchTarget, resolve: () -> PlaybackSource) {
        if (resolvingPlayback || resumeRequest != null) return
        playbackError = null
        resolvingPlayback = true
        scope.launch {
            try {
                if (repository is ParentalCatalogRepository) repository.authorizeEpisode(session.connection, target)
                val progress = model.resumeProgress(target, session)
                if (progress?.canResume == true) resumeRequest = ResumeRequest(target, progress, resolve)
                else openPlayback(target, resolve, 0)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { playbackError = safeMessage(error) }
            finally { resolvingPlayback = false }
        }
    }
    fun playFromDetails(target: WatchTarget, restart: Boolean, resolve: () -> PlaybackSource) {
        if (resolvingPlayback || resumeRequest != null) return
        playbackError = null
        resolvingPlayback = true
        scope.launch {
            try {
                if (repository is ParentalCatalogRepository) repository.authorizeEpisode(session.connection, target)
                val progress = model.resumeProgress(target, session)
                openPlayback(target, resolve, if (!restart && progress?.canResume == true) progress.positionMs else 0,
                    restart = restart || progress?.completed == true)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { playbackError = safeMessage(error) }
            finally { resolvingPlayback = false }
        }
    }
    resumeRequest?.let { request ->
        ResumeDialog(request.progress, cancel = { resumeRequest = null }, resume = {
            resumeRequest = null; launchPlayback(request.target, request.resolve, request.progress.positionMs)
        }, restart = { resumeRequest = null; launchPlayback(request.target, request.resolve, 0, restart = true) })
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (resolvingPlayback) LinearProgressIndicator(Modifier.fillMaxWidth())
        playbackError?.let { message ->
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message.localized(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { playbackError = null }) { Text(stringResource(R.string.dismiss)) }
            }
        }
        NavHost(navigation, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                ScreenFrame(session.connection.name, action = {
                    IconButton(onClick = { navigation.navigate("settings") { launchSingleTop = true } }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                }) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContentType.entries.forEach { type ->
                            NavigationCard(when (type) {
                                ContentType.LIVE -> R.drawable.ic_live_tv
                                ContentType.MOVIE -> R.drawable.ic_movie
                                ContentType.SERIES -> R.drawable.ic_series
                            }, type.label(), onClick = { navigation.navigate("browse/${type.name}/root") })
                        }
                        NavigationCard(R.drawable.ic_calendar, stringResource(R.string.tv_guide), onClick = { navigation.navigate("guide_browse/root") })
                        NavigationCard(R.drawable.ic_history, stringResource(R.string.watch_history), onClick = { navigation.navigate("history") })
                        NavigationCard(R.drawable.ic_download, stringResource(R.string.downloads), onClick = { openDownloads(false) })
                    }
                }
            }
            composable("settings") {
                ScreenFrame(stringResource(R.string.settings), back = { navigation.popBackStack() }) {
                    SettingsScreen(account = { navigation.navigate("settings/account") }, server = { navigation.navigate("settings/server") },
                        parental = { navigation.navigate("settings/parental") }, viewing = { navigation.navigate("settings/viewing") },
                        downloads = { openDownloads(true) })
                }
            }
            composable("settings/viewing") {
                val state by model.viewingData.collectAsStateWithLifecycle()
                ScreenFrame(stringResource(R.string.watch_storage_title), back = { navigation.popBackStack() }) {
                    ViewingDataSettingsScreen(state, model::clearViewingData)
                }
            }
            composable("settings/account") {
                ScreenFrame(stringResource(R.string.settings_account), back = { navigation.popBackStack() }) {
                    AccountSettingsScreen(session, model::signOut)
                }
            }
            composable("settings/server") {
                ScreenFrame(stringResource(R.string.settings_server), back = { navigation.popBackStack() }) {
                    ServerSettingsScreen(session)
                }
            }
            composable("settings/parental") {
                val parental: ParentalControlViewModel = viewModel(factory = viewModelFactory {
                    initializer { ParentalControlViewModel(container.parentalControls, container.unfilteredRepository, session.connection) }
                })
                ScreenFrame(stringResource(R.string.parental_controls), back = { navigation.popBackStack() }) {
                    ParentalControlScreen(parental, session)
                }
            }
            composable("history") {
                val history by model.history.collectAsStateWithLifecycle()
                RefreshWatchHistory(model)
                ScreenFrame(stringResource(R.string.watch_history), back = { navigation.popBackStack() }) {
                    WatchHistoryScreen(history, retry = { model.refreshHistory() }, play = { target ->
                        startPlayback(target) {
                            target.episode?.let { repository.episodeStream(session.connection, it) }
                                ?: repository.stream(session.connection, target.item, liveFormat(session))
                        }
                    }, details = { target ->
                        model.select(target.item)
                        navigation.navigate("details/${target.item.type.name}/${Uri.encode(target.item.id)}")
                    }, loadMore = model::loadMoreHistory)
                }
            }
            composable("browse/{type}/{category}") { entry ->
                val type = ContentType.valueOf(checkNotNull(entry.arguments?.getString("type")))
                val categoryId = entry.arguments?.getString("category")?.takeUnless { it == "root" }
                val browser: CatalogViewModel = viewModel(key = "catalog:${policy.revision}", factory = viewModelFactory {
                    initializer { CatalogViewModel(repository, session.connection, type, categoryId) }
                })
                val state by browser.state.collectAsStateWithLifecycle()
                ScreenFrame(state.title.ifBlank { type.label() }, back = { navigation.popBackStack() }) {
                    CatalogScreen(state, type, browser::load,
                        queryChanged = browser::setQuery, showAll = browser::showAll,
                        retryItems = browser::loadAllItems,
                        openCategory = { navigation.navigate("browse/${type.name}/${Uri.encode(it)}") },
                        openItem = { item ->
                            if (item.type == ContentType.LIVE) startPlayback(WatchTarget(item)) {
                                repository.stream(session.connection, item, liveFormat(session))
                            } else {
                                model.select(item)
                                navigation.navigate("details/${item.type.name}/${item.id}")
                            }
                        })
                }
            }
            composable("guide_browse/{category}") { entry ->
                val category = entry.arguments?.getString("category")?.takeUnless { it == "root" }
                val browser: CatalogViewModel = viewModel(key = "guide-catalog:${policy.revision}", factory = viewModelFactory {
                    initializer { CatalogViewModel(repository, session.connection, ContentType.LIVE, category) }
                })
                val state by browser.state.collectAsStateWithLifecycle()
                ScreenFrame(state.title.ifBlank { stringResource(R.string.tv_guide) }, back = { navigation.popBackStack() }) {
                    CatalogScreen(state, ContentType.LIVE, browser::load, browser::setQuery, browser::showAll, browser::loadAllItems,
                        openCategory = { navigation.navigate("guide_browse/${Uri.encode(it)}") },
                        openItem = { model.select(it); navigation.navigate("guide/${it.id}") })
                }
            }
            composable("guide/{id}") {
                val item = model.selectedItem?.takeIf { it.type == ContentType.LIVE }
                ScreenFrame(item?.title ?: stringResource(R.string.tv_guide), back = { navigation.popBackStack() }) {
                    if (item != null) {
                        val guide: GuideViewModel = viewModel(key = "guide:${policy.revision}", factory = viewModelFactory {
                            initializer { GuideViewModel(repository, session.connection, item) }
                        })
                        ProgrammeGuideScreen(guide, item,
                            watchLive = { startPlayback(WatchTarget(item)) { repository.stream(session.connection, item, liveFormat(session)) } },
                            replay = { programme -> startPlayback(WatchTarget(item)) { repository.catchupStream(session.connection, item, programme) } })
                    } else Text(stringResource(R.string.select_title), Modifier.padding(24.dp))
                }
            }
            composable("details/{type}/{id}") {
                val item = model.selectedItem
                if (item != null) {
                    val details: DetailsViewModel = viewModel(key = "details:${policy.revision}", factory = viewModelFactory {
                        initializer { DetailsViewModel(repository, session.connection, item, container.playbackProgressStore) }
                    })
                    val state by details.state.collectAsStateWithLifecycle()
                    val progress by details.progress.collectAsStateWithLifecycle()
                    val revision by model.progressRevision.collectAsStateWithLifecycle()
                    val owner = LocalLifecycleOwner.current
                    LaunchedEffect(details, owner, revision) {
                        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { details.refreshProgress()?.join(); awaitCancellation() }
                    }
                    ScreenFrame((state.movie?.item?.title ?: state.series?.title ?: item.title)
                        .ifBlank { stringResource(R.string.untitled) }, back = { navigation.popBackStack() }) {
                        val downloads: DownloadsViewModel = viewModel(factory = viewModelFactory { initializer { DownloadsViewModel(container.downloads) } })
                        val downloaded by container.downloads.store.entries.collectAsStateWithLifecycle()
                        val downloadState by downloads.state.collectAsStateWithLifecycle()
                        Column(Modifier.fillMaxSize()) {
                            if (downloadState.failed) Text(stringResource(R.string.download_action_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                            Box(Modifier.weight(1f)) { DetailsScreen(state, item, details::load,
                                playMovie = { movie, restart -> playFromDetails(WatchTarget(movie), restart) { repository.stream(session.connection, movie) } },
                                playEpisode = { episode, restart -> playFromDetails(WatchTarget(item, episode), restart) { repository.episodeStream(session.connection, episode) } },
                                progress = progress, retryProgress = { details.refreshProgress() }, setWatched = details::setWatched,
                                download = { target -> downloads.enqueue(session.connection, target, repository) },
                                downloaded = downloaded.filter { it.account == io.github.antocanza.iptvreceiver.data.accountKey(session.connection) }.map { it.target.key }.toSet(),
                                downloadBusy = downloadState.busy,
                                openDownloads = { openDownloads(false) }) }
                        }
                    }
                } else ScreenFrame(stringResource(R.string.app_name), back = { navigation.popBackStack() }) {
                    Text(stringResource(R.string.select_title), Modifier.padding(24.dp))
                }
            }
            composable("player") {
                val source = model.playbackSource
                if (source != null) {
                    val activity = LocalActivity.current
                    val context = LocalContext.current.applicationContext
                    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val target = model.watchTarget
                    val playback: PlaybackViewModel = viewModel(factory = viewModelFactory {
                        initializer { PlaybackViewModel(ExoPlaybackEngine(context, source) {
                            if (target != null) model.recordWatch(target, session)
                        }, landscape, startPositionMs = model.playbackStartMs,
                            onProgress = if (target != null && target.item.type != ContentType.LIVE) { progress ->
                                model.recordProgress(target, session, progress, source)
                            } else null) }
                    })
                    val fullscreen by playback.fullscreen.collectAsStateWithLifecycle()
                    val inPip = inPictureInPicture()
                    LaunchedEffect(landscape, inPip) { if (!inPip) playback.onOrientationChanged(landscape) }
                    fun changeFullscreen(enabled: Boolean) {
                        playback.setFullscreen(enabled)
                        activity?.requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    BackHandler(enabled = fullscreen) { changeFullscreen(false) }
                    ScreenFrame(source.title.ifBlank { stringResource(R.string.untitled) },
                        back = { navigation.popBackStack() }, showLanguage = false, fullscreen = fullscreen) {
                        PlaybackScreen(playback, fullscreen, ::changeFullscreen, onGuide = target?.item
                            ?.takeIf { it.type == ContentType.LIVE }?.let { channel -> {
                                model.select(channel)
                                if (navigation.previousBackStackEntry?.destination?.route == "guide/{id}") navigation.popBackStack()
                                else navigation.navigate("guide/${channel.id}") { popUpTo("player") { inclusive = true } }
                            } })
                    }
                } else ScreenFrame(stringResource(R.string.app_name), back = { navigation.popBackStack() }) {
                    Text(stringResource(R.string.select_title_play), Modifier.padding(24.dp))
                }
            }
        }
    }
}

private fun liveFormat(session: Session): String = session.account.preferredLiveFormat

@Composable
internal fun ScreenFrame(title: String, back: (() -> Unit)? = null, showLanguage: Boolean = false, fullscreen: Boolean = false,
    action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    val immersive = fullscreen || inPictureInPicture()
    Scaffold(contentWindowInsets = if (immersive) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
        if (!immersive) Column {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (back != null) IconButton(onClick = back) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2)
                if (showLanguage) LanguageSelector()
                action()
            }
            HorizontalDivider()
        }
    }) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() } }
}

@Composable
internal fun Loading() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
internal fun ErrorState(message: FailureReason, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message.localized(), color = MaterialTheme.colorScheme.error)
        Button(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
internal fun ContentType.label(): String = stringResource(when (this) {
    ContentType.LIVE -> R.string.live_tv
    ContentType.MOVIE -> R.string.movies
    ContentType.SERIES -> R.string.series
})
