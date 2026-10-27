package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.data.WatchHistoryEntry
import io.github.antocanza.iptvreceiver.data.WatchHistoryStore
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.data.ParentalCatalogRepository
import io.github.antocanza.iptvreceiver.model.Account
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class Session(val connection: ProviderConnection, val account: Account)
data class LoginState(val busy: Boolean = true, val session: Session? = null, val error: FailureReason? = null)
data class HistoryState(val busy: Boolean = true, val entries: List<WatchHistoryEntry> = emptyList(), val failed: Boolean = false)

class ReceiverViewModel(
    private val repository: CatalogRepository,
    private val store: SavedConnectionStore,
    private val historyStore: WatchHistoryStore,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()
    private val mutableHistory = MutableStateFlow(HistoryState())
    val history = mutableHistory.asStateFlow()
    private val historyMutex = Mutex()
    var selectedItem: CatalogItem? = null
        private set
    var playbackSource: PlaybackSource? = null
        private set
    var watchTarget: WatchTarget? = null
        private set
    private var connectionJob: Job? = null

    init {
        if (repository is ParentalCatalogRepository) viewModelScope.launch {
            repository.policy.collect {
                mutableHistory.value = HistoryState()
                refreshHistory()
            }
        }
        connectionJob = viewModelScope.launch {
            try {
                val saved = withContext(storageDispatcher) { store.load() }
                if (saved == null) mutableState.value = LoginState(busy = false)
                else authenticate(saved, remember = true)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = LoginState(busy = false, error = FailureReason.UNKNOWN)
            }
        }
    }

    fun connect(connection: ProviderConnection, remember: Boolean) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch { authenticate(connection, remember) }
    }

    private suspend fun authenticate(connection: ProviderConnection, remember: Boolean) {
        mutableState.value = LoginState(busy = true)
        try {
            val account = repository.authenticate(connection)
            try {
                withContext(storageDispatcher) { if (remember) store.save(connection) else store.clear() }
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = LoginState(busy = false, error = FailureReason.SAVE_FAILED)
                return
            }
            mutableState.value = LoginState(busy = false, session = Session(connection, account))
            mutableHistory.value = HistoryState()
            refreshHistory()
        } catch (error: CancellationException) { throw error } catch (error: Exception) {
            val reason = when (val failure = safeMessage(error)) {
                FailureReason.ACCESS_DENIED -> FailureReason.AUTHENTICATION_FAILED
                FailureReason.UNREACHABLE, FailureReason.UNAVAILABLE, FailureReason.EMPTY_RESPONSE,
                FailureReason.UNSUPPORTED_RESPONSE, FailureReason.RESPONSE_UNREADABLE, FailureReason.UNKNOWN -> FailureReason.LOGIN_FAILED
                else -> failure
            }
            mutableState.value = LoginState(busy = false, error = reason)
        }
    }

    fun select(item: CatalogItem) { selectedItem = item }

    fun preparePlayback(source: PlaybackSource, target: WatchTarget) {
        playbackSource = source
        watchTarget = target
    }

    fun refreshHistory(): Job = updateHistory { historyStore.load(it) }

    fun recordWatch(target: WatchTarget, session: Session): Job =
        updateHistory(session) { historyStore.record(it, target) }

    private fun updateHistory(
        session: Session? = mutableState.value.session,
        update: (ProviderConnection) -> List<WatchHistoryEntry>,
    ): Job = viewModelScope.launch {
        if (session == null) return@launch
        historyMutex.withLock {
            if (mutableState.value.session !== session) return@withLock
            val revision = (repository as? ParentalCatalogRepository)?.policy?.value?.revision
            try {
                val entries = withContext(storageDispatcher) {
                    val stored = update(session.connection)
                    if (repository is ParentalCatalogRepository) repository.visibleHistory(session.connection, stored) else stored
                }
                if (mutableState.value.session === session &&
                    revision == (repository as? ParentalCatalogRepository)?.policy?.value?.revision) {
                    mutableHistory.value = HistoryState(false, entries)
                }
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                if (mutableState.value.session === session) {
                    mutableHistory.value = HistoryState(busy = false, failed = true)
                }
            }
        }
    }

    fun signOut() {
        connectionJob?.cancel()
        selectedItem = null
        playbackSource = null
        watchTarget = null
        mutableHistory.value = HistoryState()
        mutableState.value = LoginState(busy = true)
        connectionJob = viewModelScope.launch {
            try {
                withContext(storageDispatcher) { store.clear() }
                mutableState.value = LoginState(busy = false)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = LoginState(busy = false, error = FailureReason.REMOVE_FAILED)
            }
        }
    }
}

internal fun safeMessage(error: Exception): FailureReason =
    if (error is ProviderException) error.reason else FailureReason.UNKNOWN
