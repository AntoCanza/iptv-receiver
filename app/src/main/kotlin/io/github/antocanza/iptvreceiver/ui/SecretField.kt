package io.github.antocanza.iptvreceiver.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.antocanza.iptvreceiver.R

@Composable
internal fun SecretField(value: String, changed: (String) -> Unit, label: String, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, changed, Modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled, singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { ActionIcon(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
            stringResource(if (visible) R.string.hide_password else R.string.show_password)) { visible = !visible } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
