package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.data.SavedConnectionStore
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
import kotlinx.coroutines.withContext

data class Session(val connection: ProviderConnection, val account: Account)
data class LoginState(val busy: Boolean = true, val session: Session? = null, val error: FailureReason? = null)

class ReceiverViewModel(
    private val repository: CatalogRepository,
    private val store: SavedConnectionStore,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginState())
    val state = mutableState.asStateFlow()
    var selectedItem: CatalogItem? = null
        private set
    var playbackSource: PlaybackSource? = null
    private var connectionJob: Job? = null

    init {
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
        } catch (error: CancellationException) { throw error } catch (error: Exception) {
            mutableState.value = LoginState(busy = false, error = safeMessage(error))
        }
    }

    fun select(item: CatalogItem) { selectedItem = item }

    fun signOut() {
        connectionJob?.cancel()
        selectedItem = null
        playbackSource = null
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
