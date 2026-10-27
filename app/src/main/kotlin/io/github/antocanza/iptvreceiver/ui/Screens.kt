package io.github.antocanza.iptvreceiver.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.ReceiverApplication
import io.github.antocanza.iptvreceiver.data.ParentalCatalogRepository
import io.github.antocanza.iptvreceiver.data.WatchTarget
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

@Composable
fun LoginScreen(state: LoginState, connect: (ProviderConnection, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    // Credentials stay out of Android's saved-state bundles.
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberConnection by rememberSaveable { mutableStateOf(false) }
    val defaultConnectionName = stringResource(R.string.default_connection_name)
    ScreenFrame(stringResource(R.string.app_name), showLanguage = true) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.connection_help))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.connection_name)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_server), contentDescription = null) },
                    label = { Text(stringResource(R.string.server_address)) }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_account), contentDescription = null) },
                    label = { Text(stringResource(R.string.username)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) }, enabled = !state.busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !state.busy) {
                            Icon(painterResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberConnection, { rememberConnection = it }, enabled = !state.busy)
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
fun ReceiverNavigation(repository: CatalogRepository, session: Session, model: ReceiverViewModel) {
    val navigation = rememberNavController()
    val container = LocalContext.current.applicationContext as ReceiverApplication
    val policy by container.parentalControls.policy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var resolvingPlayback by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<FailureReason?>(null) }
    fun startPlayback(target: WatchTarget, resolve: () -> PlaybackSource) {
        if (resolvingPlayback) return
        resolvingPlayback = true
        scope.launch {
            try {
                if (repository is ParentalCatalogRepository) repository.authorizeEpisode(session.connection, target)
                model.preparePlayback(resolve(), target)
                navigation.navigate("player") { launchSingleTop = true }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { playbackError = safeMessage(error) }
            finally { resolvingPlayback = false }
        }
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
                        NavigationCard(R.drawable.ic_history, stringResource(R.string.watch_history), onClick = { navigation.navigate("history") })
                    }
                }
            }
            composable("settings") {
                ScreenFrame(stringResource(R.string.settings), back = { navigation.popBackStack() }) {
                    SettingsScreen(account = { navigation.navigate("settings/account") }, server = { navigation.navigate("settings/server") },
                        parental = { navigation.navigate("settings/parental") })
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
                    })
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
            composable("details/{type}/{id}") {
                val item = model.selectedItem
                if (item != null) {
                    val details: DetailsViewModel = viewModel(factory = viewModelFactory {
                        initializer { DetailsViewModel(repository, session.connection, item) }
                    })
                    val state by details.state.collectAsStateWithLifecycle()
                    ScreenFrame((state.movie?.item?.title ?: state.series?.title ?: item.title)
                        .ifBlank { stringResource(R.string.untitled) }, back = { navigation.popBackStack() }) {
                        DetailsScreen(state, item, details::load,
                            playMovie = { movie -> startPlayback(WatchTarget(movie)) { repository.stream(session.connection, movie) } },
                            playEpisode = { episode -> startPlayback(WatchTarget(item, episode)) { repository.episodeStream(session.connection, episode) } })
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
                        }, landscape) }
                    })
                    val fullscreen by playback.fullscreen.collectAsStateWithLifecycle()
                    LaunchedEffect(landscape) { playback.onOrientationChanged(landscape) }
                    fun changeFullscreen(enabled: Boolean) {
                        playback.setFullscreen(enabled)
                        activity?.requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    BackHandler(enabled = fullscreen) { changeFullscreen(false) }
                    ScreenFrame(source.title.ifBlank { stringResource(R.string.untitled) },
                        back = { navigation.popBackStack() }, showLanguage = false, fullscreen = fullscreen) {
                        PlaybackScreen(playback, fullscreen, ::changeFullscreen)
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
private fun CatalogScreen(
    state: CatalogState,
    type: ContentType,
    retry: () -> Unit,
    queryChanged: (String) -> Unit,
    showAll: (Boolean) -> Unit,
    retryItems: () -> Unit,
    openCategory: (String) -> Unit,
    openItem: (CatalogItem) -> Unit,
) {
    val query = state.query.trim()
    val minimumCardWidth = if (LocalConfiguration.current.fontScale > 1.4f) 196.dp else 144.dp
    val allItemsVisible = state.canBrowseAll && (state.showAll || query.isNotBlank())
    var sortName by rememberSaveable { mutableStateOf(CatalogSort.PROVIDER.name) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    val sort = CatalogSort.valueOf(sortName)
    // These inputs reset a new result set, while Navigation restores the saved position on Back.
    val gridState = rememberSaveable(query, state.showAll, sortName, genre,
        saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver) {
        androidx.compose.foundation.lazy.grid.LazyGridState()
    }
    val visibleKeys by remember(gridState) { derivedStateOf {
        gridState.layoutInfo.visibleItemsInfo.filter {
            it.offset.y < gridState.layoutInfo.viewportEndOffset &&
                it.offset.y + it.size.height > gridState.layoutInfo.viewportStartOffset
        }.mapTo(mutableSetOf()) { it.key }
    } }
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val categories = remember(state.categories, query, allItemsVisible) {
        if (allItemsVisible) emptyList() else state.categories.filter { it.name.contains(query, ignoreCase = true) }
    }
    val sorted = remember(state.items, sort) { sortCatalog(state.items, sort) }
    val genres = remember(state.items) { state.items.flatMap { it.metadata.genres }.distinctBy { it.lowercase(java.util.Locale.ROOT) }.sorted() }
    val selectedGenre = genre?.takeIf { it in genres }
    val titles = remember(sorted, query, selectedGenre, state.canBrowseAll, allItemsVisible) {
        if (state.canBrowseAll && !allItemsVisible) emptyList()
        else sorted.filter { it.matchesCatalog(query, selectedGenre) }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(state.query, queryChanged, Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { queryChanged("") }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
            } },
            label = { Text(stringResource(when {
                !state.canBrowseAll -> R.string.search
                type == ContentType.SERIES -> R.string.search_all_series
                else -> R.string.search_all_movies
            })) }, singleLine = true)
        if (state.canBrowseAll && allItemsVisible) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showAll(false) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.browse_categories))
                }
                if (!state.itemsBusy && state.itemsError == null) Text(stringResource(R.string.catalog_result_count, titles.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (allItemsVisible && state.itemsBusy) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Loading() }
            return@Column
        }
        if (allItemsVisible && state.itemsError != null) {
            ErrorState(state.itemsError, retryItems)
            return@Column
        }
        if (state.items.any { it.type != ContentType.LIVE } && (!state.canBrowseAll || allItemsVisible)) {
            val sorts = listOf(CatalogSort.PROVIDER, CatalogSort.TITLE) +
                listOfNotNull(CatalogSort.NEWEST.takeIf { state.items.any { item -> item.addedAtSeconds != null } },
                    CatalogSort.RATING.takeIf { state.items.any { item -> item.metadata.rating != null } })
            CatalogRefinements(sort, sorts, selectedGenre, genres, onSort = { sortName = it.name }, onGenre = { genre = it })
        }
        LazyVerticalGrid(GridCells.Adaptive(minimumCardWidth), Modifier.weight(1f).padding(horizontal = 16.dp), state = gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.canBrowseAll && !allItemsVisible) item(key = "all-items", span = { GridItemSpan(maxLineSpan) }) {
                Card(onClick = { showAll(true) }, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(if (type == ContentType.SERIES) R.drawable.ic_series else R.drawable.ic_movie), contentDescription = null)
                        Text(stringResource(if (type == ContentType.SERIES) R.string.all_series else R.string.all_movies),
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            items(categories, key = { "category:${it.id}" }) { category ->
                Card(onClick = { openCategory(category.id) }, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(category.name.ifBlank { stringResource(R.string.unnamed_category) }, style = MaterialTheme.typography.titleMedium,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            items(titles, key = { "item:${it.id}" }) { item ->
                Card(onClick = { openItem(item) }, Modifier.fillMaxWidth()) {
                    CatalogArtwork(item, visible = "item:${item.id}" in visibleKeys)
                    Text(item.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium,
                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.type != ContentType.LIVE) CatalogMetadata(item.metadata)
                }
            }
            if (categories.isEmpty() && titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(if (query.isNotBlank() || selectedGenre != null) R.string.no_search_results else R.string.empty_category), Modifier.padding(24.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ScreenFrame(title: String, back: (() -> Unit)? = null, showLanguage: Boolean = false, fullscreen: Boolean = false,
    action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(contentWindowInsets = if (fullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
        if (!fullscreen) Column {
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
