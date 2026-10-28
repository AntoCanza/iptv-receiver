package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
import io.github.antocanza.iptvreceiver.data.WatchHistoryEntry
import io.github.antocanza.iptvreceiver.data.WatchHistoryStore
import io.github.antocanza.iptvreceiver.data.HistoryPage
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.data.ParentalCatalogRepository
import io.github.antocanza.iptvreceiver.data.PlaybackProgress
import io.github.antocanza.iptvreceiver.data.PlaybackProgressStore
import io.github.antocanza.iptvreceiver.data.SubscriptionNotice
import io.github.antocanza.iptvreceiver.data.SubscriptionReminders
import io.github.antocanza.iptvreceiver.model.Account
import io.github.antocanza.iptvreceiver.model.CatalogItem
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.FailureReason
import io.github.antocanza.iptvreceiver.model.PlaybackSource
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import io.github.antocanza.iptvreceiver.model.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
data class HistoryState(val busy: Boolean = true, val entries: List<WatchHistoryEntry> = emptyList(), val failed: Boolean = false,
    val hasMore: Boolean = false)
data class ViewingDataState(val busy: Boolean = false, val failed: Boolean = false, val cleared: Boolean = false)

class ReceiverViewModel(
    private val repository: CatalogRepository,
    private val store: SavedConnectionStore,
    private val historyStore: WatchHistoryStore,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val progressStore: PlaybackProgressStore? = null,
    private val reminders: SubscriptionReminders? = null,
    private val persistenceScope: CoroutineScope? = null,
    private val onAuthenticated: suspend (ProviderConnection) -> Unit = {},
    private val onSignOut: suspend () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()
    private val mutableHistory = MutableStateFlow(HistoryState())
    val history = mutableHistory.asStateFlow()
    private val mutableProgressRevision = MutableStateFlow(0L)
    val progressRevision = mutableProgressRevision.asStateFlow()
    private val mutableViewingData = MutableStateFlow(ViewingDataState())
    val viewingData = mutableViewingData.asStateFlow()
    private var historyLimit = 50
    @Volatile private var viewingGeneration = 0L
    private val mutableExpiryNotice = MutableStateFlow<SubscriptionNotice?>(null)
    val expiryNotice = mutableExpiryNotice.asStateFlow()
    private var reminderAcknowledgement: Job? = null
    private var reminderRevision = 0L
    private val historyMutex = Mutex()
    var selectedItem: CatalogItem? = null
        private set
    var playbackSource: PlaybackSource? = null
        private set
    var watchTarget: WatchTarget? = null
        private set
    var playbackStartMs: Long = 0
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
        mutableExpiryNotice.value = null
        mutableState.value = LoginState(busy = true)
        try {
            val account = repository.authenticate(connection)
            try {
                withContext(storageDispatcher) { if (remember) store.save(connection) else store.clear() }
                onAuthenticated(connection)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = LoginState(busy = false, error = FailureReason.SAVE_FAILED)
                return
            }
            mutableState.value = LoginState(busy = false, session = Session(connection, account))
            historyLimit = 50
            mutableViewingData.value = ViewingDataState()
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

    fun preparePlayback(source: PlaybackSource, target: WatchTarget, startPositionMs: Long = 0) {
        playbackSource = source
        watchTarget = target
        playbackStartMs = startPositionMs
    }

    suspend fun resumeProgress(target: WatchTarget, session: Session): PlaybackProgress? = withContext(storageDispatcher) {
        if (target.item.type == io.github.antocanza.iptvreceiver.model.ContentType.LIVE) null else progressStore?.read(session.connection, target)
    }

    fun recordProgress(target: WatchTarget, session: Session, progress: PlaybackProgress, source: PlaybackSource? = null): Job? {
        val destination = progressStore ?: return null
        if (mutableState.value.session !== session) return null
        if (source != null && watchTarget?.key == target.key && playbackSource !== source) return null
        val observed = System.nanoTime()
        val generation = viewingGeneration
        val checkpoint = if (source != null) progress.copy(lastPlayedAtMillis = System.currentTimeMillis()) else progress
        return (persistenceScope ?: viewModelScope).launch(storageDispatcher) {
            historyMutex.withLock {
                if (generation != viewingGeneration) return@withLock
                try {
                    destination.save(session.connection, target, checkpoint, observed)
                    if (mutableState.value.session === session) {
                        val current = destination.read(session.connection, target)
                        mutableProgressRevision.value++
                        mutableHistory.value = mutableHistory.value.copy(entries = mutableHistory.value.entries.map {
                            if (it.target.key == target.key) it.copy(progress = current) else it
                        })
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { if (mutableState.value.session === session) mutableHistory.value = mutableHistory.value.copy(failed = true) }
            }
        }
    }

    suspend fun restartProgress(target: WatchTarget, session: Session) = withContext(storageDispatcher) {
        historyMutex.withLock {
            if (mutableState.value.session === session) {
                progressStore?.restart(session.connection, target)
                mutableProgressRevision.value++
            }
        }
    }

    fun clearViewingData() {
        val session = mutableState.value.session ?: return
        if (mutableViewingData.value.busy) return
        viewingGeneration++
        mutableViewingData.value = ViewingDataState(busy = true)
        (persistenceScope ?: viewModelScope).launch(storageDispatcher) {
            historyMutex.withLock {
                try {
                    historyStore.clear(session.connection)
                    progressStore?.clear(session.connection)
                    if (mutableState.value.session === session) {
                        mutableHistory.value = HistoryState(busy = false)
                        mutableProgressRevision.value++
                        mutableViewingData.value = ViewingDataState(cleared = true)
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    if (mutableState.value.session === session) mutableViewingData.value = ViewingDataState(failed = true)
                }
            }
        }
    }

    fun checkExpiryReminder(): Job = viewModelScope.launch {
        if (reminderAcknowledgement?.isActive == true) return@launch
        val session = mutableState.value.session ?: return@launch
        val revision = reminderRevision
        val notice = withContext(storageDispatcher) { reminders?.due(session.connection, session.account.expiresAtSeconds) }
        if (mutableState.value.session === session && reminderRevision == revision) mutableExpiryNotice.value = notice
    }

    fun acknowledgeExpiryReminder() {
        val session = mutableState.value.session ?: return
        val notice = mutableExpiryNotice.value ?: return
        reminderRevision++
        mutableExpiryNotice.value = null
        reminderAcknowledgement = (persistenceScope ?: viewModelScope).launch(storageDispatcher) {
            reminders?.acknowledge(session.connection, notice)
        }
    }

    fun refreshHistory(): Job = updateHistory { historyStore.page(it, historyLimit) }

    fun loadMoreHistory() {
        if (mutableHistory.value.busy || !mutableHistory.value.hasMore) return
        historyLimit = (historyLimit + 50).coerceAtMost(100_000)
        mutableHistory.value = mutableHistory.value.copy(busy = true)
        refreshHistory()
    }

    fun recordWatch(target: WatchTarget, session: Session): Job =
        updateHistory(session) { historyStore.record(it, target); historyStore.page(it, historyLimit) }

    private fun updateHistory(
        session: Session? = mutableState.value.session,
        update: (ProviderConnection) -> HistoryPage,
    ): Job {
        val generation = viewingGeneration
        return viewModelScope.launch {
            if (session == null) return@launch
            historyMutex.withLock {
                if (mutableState.value.session !== session || generation != viewingGeneration) return@withLock
                val revision = (repository as? ParentalCatalogRepository)?.policy?.value?.revision
                try {
                    val page = withContext(storageDispatcher) {
                        val stored = update(session.connection)
                        val visible = if (repository is ParentalCatalogRepository) repository.visibleHistory(session.connection, stored.entries) else stored.entries
                        stored.copy(entries = visible.map { entry -> entry.copy(progress = progressStore?.read(session.connection, entry.target)) })
                    }
                    if (mutableState.value.session === session &&
                        revision == (repository as? ParentalCatalogRepository)?.policy?.value?.revision) {
                        mutableHistory.value = HistoryState(false, page.entries, hasMore = page.hasMore)
                    }
                } catch (error: CancellationException) { throw error } catch (_: Exception) {
                    if (mutableState.value.session === session) {
                        mutableHistory.value = HistoryState(busy = false, failed = true)
                    }
                }
            }
        }
    }

    fun signOut() {
        connectionJob?.cancel()
        selectedItem = null
        playbackSource = null
        watchTarget = null
        playbackStartMs = 0
        mutableExpiryNotice.value = null
        mutableHistory.value = HistoryState()
        historyLimit = 50
        mutableViewingData.value = ViewingDataState()
        mutableState.value = LoginState(busy = true)
        connectionJob = viewModelScope.launch {
            try {
                withContext(storageDispatcher) { try { onSignOut() } finally { store.clear() } }
                mutableState.value = LoginState(busy = false)
            } catch (error: CancellationException) { throw error } catch (_: Exception) {
                mutableState.value = LoginState(busy = false, error = FailureReason.REMOVE_FAILED)
            }
        }
    }
}

internal fun safeMessage(error: Exception): FailureReason =
    if (error is ProviderException) error.reason else FailureReason.UNKNOWN
