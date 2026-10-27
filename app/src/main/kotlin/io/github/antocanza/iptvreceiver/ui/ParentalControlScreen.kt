package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.antocanza.iptvreceiver.R
import io.github.antocanza.iptvreceiver.data.categoryKey
import io.github.antocanza.iptvreceiver.model.ContentType

@Composable
internal fun ParentalControlScreen(model: ParentalControlViewModel, session: Session) {
    val state by model.state.collectAsStateWithLifecycle()
    // Passwords and access stay in memory; leaving this screen or backgrounding relocks it.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var changing by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ContentType.LIVE) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(model, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                model.lock(); password = ""; confirmation = ""; changing = false; confirmReset = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); model.lock() }
    }
    LaunchedEffect(state.busy, state.unlocked, state.error) {
        if (!state.busy && state.unlocked && state.error == null) {
            password = ""; confirmation = ""; changing = false
        }
    }
    val creating = !state.policy.passwordSet
    val categories = state.categories[type].orEmpty()
    val blockedIds = remember(state.policy, categories, type) { state.policy.blockedIds(session.connection, type, categories) }
    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.parental_default_help), Modifier.padding(top = 16.dp)) }
        if (state.policy.storageFailed) item {
            Text(stringResource(R.string.parental_storage_error), color = MaterialTheme.colorScheme.error)
        }
        if ((!state.unlocked || changing) && !state.policy.storageFailed) item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (creating) R.string.parental_create else if (changing) R.string.parental_change_password else R.string.parental_unlock),
                    style = MaterialTheme.typography.titleMedium)
                if (creating || changing) Text(stringResource(R.string.parental_remember_password))
                OutlinedTextField(password, { password = it.take(128) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) }, singleLine = true, enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (creating || changing) OutlinedTextField(confirmation, { confirmation = it.take(128) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.parental_confirm_password)) }, singleLine = true, enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Button(onClick = { model.submit(password, confirmation, changing) }, enabled = !state.busy && password.isNotEmpty()) {
                    Text(stringResource(if (creating) R.string.parental_create else if (changing) R.string.parental_save else R.string.parental_unlock))
                }
                if (changing) TextButton(onClick = { changing = false; password = ""; confirmation = "" }) { Text(stringResource(R.string.cancel)) }
            }
        }
        state.error?.let { error -> item { Text(stringResource(error), color = MaterialTheme.colorScheme.error) } }
        if (state.busy) item { CircularProgressIndicator() }
        if (state.unlocked && !changing) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.parental_block_adult), Modifier.weight(1f))
                    Switch(state.policy.blockAdult, model::setAdultBlock, enabled = !state.busy)
                }
            }
            item { TextButton(onClick = { changing = true }) { Text(stringResource(R.string.parental_change_password)) } }
            item { Text(stringResource(R.string.parental_categories_help)) }
            item {
                Row {
                    ContentType.entries.forEach { option ->
                        TextButton(onClick = { type = option; query = "" }, enabled = type != option) { Text(option.label()) }
                    }
                }
            }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.parental_search_categories)) }, singleLine = true) }
            if (state.categoriesBusy) item { CircularProgressIndicator() }
            if (state.categoriesFailed) item {
                Text(stringResource(R.string.parental_categories_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::loadCategories) { Text(stringResource(R.string.retry)) }
            }
            items(categories.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { category ->
                val manual = categoryKey(session.connection, type, category.id) in state.policy.blockedCategories
                val inherited = category.id in blockedIds && !manual
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(manual || inherited, { model.setCategory(type, category.id, it) }, enabled = !state.busy && !inherited)
                    Text(category.name.ifBlank { stringResource(R.string.unnamed_category) }, Modifier.weight(1f))
                }
            }
        }
        if (state.policy.passwordSet || state.policy.storageFailed) item {
            TextButton(onClick = { confirmReset = true }, enabled = !state.busy) { Text(stringResource(R.string.parental_reset)) }
        }
        item { Text(stringResource(R.string.parental_detection_help), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp)) }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.parental_reset)) },
        text = { Text(stringResource(R.string.parental_reset_warning)) },
        confirmButton = { TextButton(onClick = { confirmReset = false; password = ""; confirmation = ""; changing = false; model.reset() }) { Text(stringResource(R.string.parental_reset)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
}
