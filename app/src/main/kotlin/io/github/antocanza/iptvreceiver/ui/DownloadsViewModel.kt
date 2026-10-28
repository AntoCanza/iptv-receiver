package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.data.DownloadRepository
import io.github.antocanza.iptvreceiver.data.WatchTarget
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadUiState(val busy: Boolean = false, val failed: Boolean = false)

class DownloadsViewModel(val downloads: DownloadRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state = mutableState.asStateFlow()
    init { refresh() }

    fun refresh() = action { downloads.refresh() }
    fun enqueue(connection: ProviderConnection, target: WatchTarget, repository: CatalogRepository) = action {
        downloads.enqueue(connection, target, repository)
    }
    fun action(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = DownloadUiState(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                mutableState.value = DownloadUiState()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = DownloadUiState(failed = true) }
        }
    }
}
