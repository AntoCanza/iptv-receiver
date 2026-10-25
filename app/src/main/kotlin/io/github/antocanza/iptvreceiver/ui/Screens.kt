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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.playback.ExoPlaybackEngine

@Composable
fun LoginScreen(state: LoginState, connect: (ProviderConnection, Boolean) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    // Credentials stay out of Android's saved-state bundles.
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberConnection by rememberSaveable { mutableStateOf(false) }
    var allowHttpStreams by rememberSaveable { mutableStateOf(false) }
    val defaultConnectionName = stringResource(R.string.default_connection_name)
    ScreenFrame(stringResource(R.string.app_name)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.connection_help))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.connection_name)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(server, { server = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.server_address)) }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.username)) }, enabled = !state.busy, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) }, enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(rememberConnection, { rememberConnection = it }, enabled = !state.busy)
                    Text(stringResource(R.string.remember_connection))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allowHttpStreams, { allowHttpStreams = it }, enabled = !state.busy)
                    Text(stringResource(R.string.allow_http_streams))
                }
                Text(stringResource(R.string.http_streams_help), style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it.localized(), color = MaterialTheme.colorScheme.error) }
                Button(onClick = { connect(ProviderConnection(name.trim().ifBlank { defaultConnectionName },
                    server.trim(), username.trim(), password, allowHttpStreams), rememberConnection) },
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
    var playbackError by remember { mutableStateOf<FailureReason?>(null) }
    fun startPlayback(resolve: () -> PlaybackSource) {
        try {
            model.playbackSource = resolve()
            navigation.navigate("player") { launchSingleTop = true }
        } catch (error: Exception) { playbackError = safeMessage(error) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        playbackError?.let { message ->
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message.localized(), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { playbackError = null }) { Text(stringResource(R.string.dismiss)) }
            }
        }
        NavHost(navigation, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") {
                ScreenFrame(session.connection.name, action = {
                    TextButton(onClick = model::signOut) { Text(stringResource(R.string.sign_out)) }
                }) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ContentType.entries.forEach { type ->
                            Card(onClick = { navigation.navigate("browse/${type.name}/root") }, Modifier.fillMaxWidth()) {
                                Text(type.label(), Modifier.padding(32.dp), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
            composable("browse/{type}/{category}") { entry ->
                val type = ContentType.valueOf(checkNotNull(entry.arguments?.getString("type")))
                val categoryId = entry.arguments?.getString("category")?.takeUnless { it == "root" }
                val browser: CatalogViewModel = viewModel(factory = viewModelFactory {
                    initializer { CatalogViewModel(repository, session.connection, type, categoryId) }
                })
                val state by browser.state.collectAsStateWithLifecycle()
                ScreenFrame(state.title.ifBlank { type.label() }, back = { navigation.popBackStack() }) {
                    CatalogScreen(state, session.connection.allowHttpStreams, browser::load,
                        queryChanged = browser::setQuery, showAllMovies = browser::showAllMovies,
                        retryMovies = browser::loadAllMovies,
                        openCategory = { navigation.navigate("browse/${type.name}/${Uri.encode(it)}") },
                        openItem = { item ->
                            if (item.type == ContentType.LIVE) startPlayback {
                                val format = session.account.liveFormats.firstOrNull { it == "ts" }
                                    ?: session.account.liveFormats.firstOrNull() ?: "ts"
                                repository.stream(session.connection, item, format)
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
                    ScreenFrame(item.title.ifBlank { stringResource(R.string.untitled) }, back = { navigation.popBackStack() }) {
                        DetailsScreen(state, item, session.connection.allowHttpStreams, details::load,
                            playMovie = { movie -> startPlayback { repository.stream(session.connection, movie) } },
                            playEpisode = { episode -> startPlayback { repository.episodeStream(session.connection, episode) } })
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
                    val playback: PlaybackViewModel = viewModel(factory = viewModelFactory {
                        initializer { PlaybackViewModel(ExoPlaybackEngine(context, source), landscape) }
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

@Composable
private fun CatalogScreen(
    state: CatalogState,
    allowHttp: Boolean,
    retry: () -> Unit,
    queryChanged: (String) -> Unit,
    showAllMovies: (Boolean) -> Unit,
    retryMovies: () -> Unit,
    openCategory: (String) -> Unit,
    openItem: (CatalogItem) -> Unit,
) {
    val query = state.query.trim()
    val allMoviesVisible = state.canBrowseAllMovies && (state.showAllMovies || query.isNotBlank())
    val gridState = rememberLazyGridState()
    LaunchedEffect(query, state.showAllMovies) { gridState.scrollToItem(0) }
    val visibleKeys by remember { derivedStateOf {
        gridState.layoutInfo.visibleItemsInfo.filter {
            it.offset.y < gridState.layoutInfo.viewportEndOffset &&
                it.offset.y + it.size.height > gridState.layoutInfo.viewportStartOffset
        }.mapTo(mutableSetOf()) { it.key }
    } }
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val categories = remember(state.categories, query, allMoviesVisible) {
        if (allMoviesVisible) emptyList() else state.categories.filter { it.name.contains(query, ignoreCase = true) }
    }
    val titles = remember(state.items, query, state.canBrowseAllMovies, allMoviesVisible) {
        if (state.canBrowseAllMovies && !allMoviesVisible) emptyList()
        else state.items.filter { it.title.contains(query, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(state.query, queryChanged, Modifier.fillMaxWidth().padding(16.dp),
            label = { Text(stringResource(if (state.canBrowseAllMovies) R.string.search_all_movies else R.string.search)) }, singleLine = true)
        if (state.canBrowseAllMovies && allMoviesVisible) {
            TextButton(onClick = { showAllMovies(false) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.browse_categories))
            }
        }
        if (allMoviesVisible && state.itemsBusy) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Loading() }
            return@Column
        }
        if (allMoviesVisible && state.itemsError != null) {
            ErrorState(state.itemsError, retryMovies)
            return@Column
        }
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f).padding(horizontal = 16.dp), state = gridState,
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.canBrowseAllMovies && !allMoviesVisible) item(key = "all-movies", span = { GridItemSpan(maxLineSpan) }) {
                Card(onClick = { showAllMovies(true) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.all_movies), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(categories, key = { "category:${it.id}" }) { category ->
                Card(onClick = { openCategory(category.id) }, Modifier.fillMaxWidth()) {
                    Text(category.name.ifBlank { stringResource(R.string.unnamed_category) }, Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(titles, key = { "item:${it.id}" }) { item ->
                Card(onClick = { openItem(item) }, Modifier.fillMaxWidth()) {
                    CatalogArtwork(item, allowHttp, visible = "item:${item.id}" in visibleKeys)
                    Text(item.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (categories.isEmpty() && titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(if (query.isNotBlank()) R.string.no_search_results else R.string.empty_category), Modifier.padding(24.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailsScreen(
    state: DetailsState,
    item: CatalogItem,
    allowHttp: Boolean,
    retry: () -> Unit,
    playMovie: (CatalogItem) -> Unit,
    playEpisode: (io.github.antocanza.iptvreceiver.model.Episode) -> Unit,
) {
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val series = state.series
    val movie = state.movie
    val listState = rememberLazyListState()
    val posterVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "details-artwork" } } }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), state = listState, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "details-artwork") { CatalogArtwork(movie?.item ?: item, allowHttp,
            Modifier.widthIn(max = 240.dp), visible = posterVisible) }
        item { Text(movie?.description ?: series?.description.orEmpty()) }
        if (movie != null) item { Button(onClick = { playMovie(movie.item) }) { Text(stringResource(R.string.play)) } }
        series?.episodes?.groupBy { it.season }?.forEach { (season, episodes) ->
            item(key = "season:$season") { Text(stringResource(R.string.season_number, season), style = MaterialTheme.typography.titleLarge) }
            items(episodes, key = { "episode:${it.id}" }) { episode ->
                Card(onClick = { playEpisode(episode) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.episode_number, episode.number,
                        episode.title.ifBlank { stringResource(R.string.untitled) }), Modifier.padding(20.dp))
                }
            }
        }
        if (series != null && series.episodes.isEmpty()) item { Text(stringResource(R.string.empty_category)) }
    }
}

@Composable
private fun ScreenFrame(title: String, back: (() -> Unit)? = null, showLanguage: Boolean = true, fullscreen: Boolean = false,
    action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(contentWindowInsets = if (fullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
        if (!fullscreen) Column {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (back != null) TextButton(onClick = back) { Text(stringResource(R.string.back)) }
                Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2)
                if (showLanguage) LanguageSelector()
                action()
            }
            HorizontalDivider()
        }
    }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@Composable
private fun Loading() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
private fun ErrorState(message: FailureReason, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message.localized(), color = MaterialTheme.colorScheme.error)
        Button(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ContentType.label(): String = stringResource(when (this) {
    ContentType.LIVE -> R.string.live_tv
    ContentType.MOVIE -> R.string.movies
    ContentType.SERIES -> R.string.series
})
