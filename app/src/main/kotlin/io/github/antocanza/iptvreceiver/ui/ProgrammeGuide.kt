package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class GuideState(val busy: Boolean = true, val programmes: List<Programme> = emptyList(), val error: FailureReason? = null,
    val channel: CatalogItem? = null)

class GuideViewModel(private val repository: CatalogRepository, private val connection: ProviderConnection,
    private val channel: CatalogItem) : ViewModel() {
    private val mutableState = MutableStateFlow(GuideState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init { load() }
    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.value = GuideState()
            try {
                // History intentionally omits provider capabilities; refresh the channel before offering archives.
                val current = repository.items(connection, ContentType.LIVE, channel.categoryId).firstOrNull { it.id == channel.id }
                    ?: throw ProviderException(FailureReason.UNAVAILABLE)
                mutableState.value = GuideState(false, repository.guide(connection, current), channel = current)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutableState.value = GuideState(busy = false, error = safeMessage(error)) }
        }
    }
}

@Composable
fun ProgrammeGuideScreen(model: GuideViewModel, channel: CatalogItem, watchLive: () -> Unit, replay: (Programme) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (isActive) { now = System.currentTimeMillis() / 1000; delay(30_000) } }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val programmes = state.programmes.filter { (if (showPast) it.endsAtSeconds <= now else it.endsAtSeconds > now) &&
        (query.isBlank() || it.title.contains(query, ignoreCase = true)) }
        .let { if (showPast) it.sortedByDescending(Programme::startsAtSeconds) else it }
    val conflicting = remember(state.programmes) { hasProgrammeOverlaps(state.programmes) }
    val current = unambiguousCurrentProgramme(state.programmes, now)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()) }
    val time = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()) }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "tools") {
            Row {
                ActionIcon(R.drawable.ic_play, stringResource(R.string.guide_watch_live)) { keyboard?.hide(); focus.clearFocus(); watchLive() }
                ActionIcon(R.drawable.ic_refresh, stringResource(R.string.guide_refresh), onClick = model::load)
                ActionIcon(R.drawable.ic_history, stringResource(R.string.guide_show_past), selected = showPast) { showPast = !showPast }
            }
        }
        item(key = "search") {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.guide_search), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = { if (query.isNotEmpty()) ActionIcon(R.drawable.ic_close, stringResource(R.string.clear_search)) { query = "" } },
                singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }))
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { failure -> item { Text(failure.localized(), color = MaterialTheme.colorScheme.error) } }
        if (conflicting) item(key = "schedule-conflict") { Text(stringResource(R.string.guide_schedule_conflict), style = MaterialTheme.typography.bodySmall) }
        if (!state.busy && state.error == null && programmes.isEmpty()) item { Text(stringResource(R.string.guide_empty), Modifier.padding(12.dp)) }
        items(programmes, key = { "${it.startsAtSeconds}:${it.endsAtSeconds}" }) { programme ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(date.format(Instant.ofEpochSecond(programme.startsAtSeconds)) + " · " +
                        time.format(Instant.ofEpochSecond(programme.startsAtSeconds)) + " – " + time.format(Instant.ofEpochSecond(programme.endsAtSeconds)),
                        style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(programme.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (programme.canReplay(state.channel ?: channel, now)) ActionIcon(R.drawable.ic_play,
                            stringResource(R.string.action_for_title, stringResource(R.string.guide_replay), programme.title)) {
                            keyboard?.hide(); focus.clearFocus(); replay(programme)
                        }
                    }
                    if (programme == current) Text(stringResource(R.string.guide_now), style = MaterialTheme.typography.labelMedium)
                    if (programme.description.isNotBlank()) {
                        var expanded by rememberSaveable(programme.id) { mutableStateOf(false) }
                        var overflows by remember(programme.id) { mutableStateOf(false) }
                        Text(programme.description, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
                        if (expanded || overflows) ActionIcon(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                            stringResource(if (expanded) R.string.metadata_show_less else R.string.metadata_read_more)) { expanded = !expanded }
                    }
                }
            }
        }
    }
}
