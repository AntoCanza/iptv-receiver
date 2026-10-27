package io.github.antocanza.iptvreceiver.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Configuration changes retain the form in memory; no SavedStateHandle or persisted credentials. */
class LoginDraftViewModel : ViewModel() {
    var server by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    fun clear() { server = ""; username = ""; password = "" }
    override fun onCleared() { clear() }
    override fun toString() = "LoginDraftViewModel([redacted])"
}
