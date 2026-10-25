package io.github.antocanza.iptvreceiver.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import coil.compose.AsyncImage
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection

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
                    CatalogScreen(state, browser::load,
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
                        DetailsScreen(state, details::load,
                            playMovie = { movie -> startPlayback { repository.stream(session.connection, movie) } },
                            playEpisode = { episode -> startPlayback { repository.episodeStream(session.connection, episode) } })
                    }
                } else ScreenFrame(stringResource(R.string.app_name), back = { navigation.popBackStack() }) {
                    Text(stringResource(R.string.select_title), Modifier.padding(24.dp))
                }
            }
            composable("player") {
                val source = model.playbackSource
                if (source != null) ScreenFrame(source.title.ifBlank { stringResource(R.string.untitled) }, back = { navigation.popBackStack() }, showLanguage = false) {
                    PlaybackScreen(source)
                } else ScreenFrame(stringResource(R.string.app_name), back = { navigation.popBackStack() }) {
                    Text(stringResource(R.string.select_title_play), Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(state: CatalogState, retry: () -> Unit, openCategory: (String) -> Unit, openItem: (CatalogItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val categories = remember(state.categories, query) { state.categories.filter { it.name.contains(query, ignoreCase = true) } }
    val titles = remember(state.items, query) { state.items.filter { it.title.contains(query, ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp),
            label = { Text(stringResource(R.string.search)) }, singleLine = true)
        LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(categories, key = { "category:${it.id}" }) { category ->
                Card(onClick = { openCategory(category.id) }, Modifier.fillMaxWidth()) {
                    Text(category.name.ifBlank { stringResource(R.string.unnamed_category) }, Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(titles, key = { "item:${it.id}" }) { item ->
                Card(onClick = { openItem(item) }, Modifier.fillMaxWidth()) {
                    if (item.imageUrl != null) AsyncImage(item.imageUrl, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(if (item.type == ContentType.LIVE) 16f / 9 else 2f / 3),
                        contentScale = if (item.type == ContentType.LIVE) ContentScale.Fit else ContentScale.Crop)
                    Text(item.title.ifBlank { stringResource(R.string.untitled) }, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (categories.isEmpty() && titles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.empty_category), Modifier.padding(24.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailsScreen(
    state: DetailsState,
    retry: () -> Unit,
    playMovie: (CatalogItem) -> Unit,
    playEpisode: (io.github.antocanza.iptvreceiver.model.Episode) -> Unit,
) {
    if (state.busy) { Loading(); return }
    state.error?.let { ErrorState(it, retry); return }
    val series = state.series
    val movie = state.movie
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun ScreenFrame(title: String, back: (() -> Unit)? = null, showLanguage: Boolean = true, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        Column {
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
