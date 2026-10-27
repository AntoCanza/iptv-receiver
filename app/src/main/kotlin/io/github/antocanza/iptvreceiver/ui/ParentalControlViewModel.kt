package io.github.antocanza.iptvreceiver.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.ParentalControls
import io.github.antocanza.iptvreceiver.data.ParentalPolicy
import io.github.antocanza.iptvreceiver.data.PasswordThrottledException
import io.github.antocanza.iptvreceiver.data.categoryKey
import io.github.antocanza.iptvreceiver.model.CatalogRepository
import io.github.antocanza.iptvreceiver.model.Category
import io.github.antocanza.iptvreceiver.model.ContentType
import io.github.antocanza.iptvreceiver.model.ProviderConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParentalPanelState(
    val policy: ParentalPolicy,
    val unlocked: Boolean = false,
    val busy: Boolean = false,
    val categoriesBusy: Boolean = false,
    val categories: Map<ContentType, List<Category>> = emptyMap(),
    val error: Int? = null,
    val categoriesFailed: Boolean = false,
)

class ParentalControlViewModel(
    private val controls: ParentalControls,
    private val repository: CatalogRepository,
    private val connection: ProviderConnection,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ParentalPanelState(controls.policy.value))
    val state = mutableState.asStateFlow()
    private var token: Long? = null
    private var job: Job? = null
    private var categoryJob: Job? = null

    init {
        viewModelScope.launch { controls.policy.collect { policy -> mutableState.value = state.value.copy(policy = policy) } }
    }

    fun submit(password: String, confirmation: String, changing: Boolean = false) {
        val creating = !controls.policy.value.passwordSet
        if ((creating || changing) && (password.length !in 6..128 || password.isBlank())) {
            mutableState.value = state.value.copy(error = R.string.parental_password_length)
            return
        }
        if ((creating || changing) && password != confirmation) {
            mutableState.value = state.value.copy(error = R.string.parental_password_mismatch)
            return
        }
        perform {
            when {
                changing -> { val access = checkNotNull(token); withContext(dispatcher) { controls.changePassword(access, password) } }
                creating -> token = withContext(dispatcher) { controls.create(password) }
                else -> token = withContext(dispatcher) { controls.unlock(password) }
            }
            if (token == null) mutableState.value = state.value.copy(error = R.string.parental_wrong_password)
            else {
                mutableState.value = state.value.copy(unlocked = true)
                loadCategories()
            }
        }
    }

    fun setAdultBlock(blocked: Boolean) = perform {
        val access = checkNotNull(token)
        withContext(dispatcher) { controls.update(access, blockAdult = blocked) }
    }

    fun setCategory(type: ContentType, id: String, blocked: Boolean) = perform {
        val access = checkNotNull(token)
        withContext(dispatcher) { controls.update(access, category = categoryKey(connection, type, id), blocked = blocked) }
    }

    fun reset() = perform {
        withContext(dispatcher) { controls.reset() }
        token = null
        mutableState.value = ParentalPanelState(controls.policy.value)
    }

    fun lock() {
        job?.cancel()
        categoryJob?.cancel()
        token = null
        mutableState.value = ParentalPanelState(controls.policy.value)
    }

    fun loadCategories() {
        if (token == null || categoryJob?.isActive == true) return
        categoryJob = viewModelScope.launch {
            mutableState.value = state.value.copy(categoriesBusy = true, categoriesFailed = false)
            val loaded = mutableMapOf<ContentType, List<Category>>()
            var failed = false
            for (type in ContentType.entries) {
                try { loaded[type] = withContext(dispatcher) { repository.categories(connection, type) } }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { failed = true }
            }
            mutableState.value = state.value.copy(categories = loaded, categoriesBusy = false, categoriesFailed = failed)
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null)
        job = viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (_: PasswordThrottledException) { mutableState.value = state.value.copy(error = R.string.parental_too_many_attempts) }
            catch (_: Exception) { mutableState.value = state.value.copy(error = R.string.parental_storage_error) }
            finally { mutableState.value = state.value.copy(busy = false, policy = controls.policy.value) }
        }
    }

}
