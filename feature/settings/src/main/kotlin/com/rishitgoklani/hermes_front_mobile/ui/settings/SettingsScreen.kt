package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(state.baseUrl, onBaseUrl, Modifier.fillMaxWidth(), label = { Text("Hermes URL") })
        OutlinedTextField(
            state.apiKey, onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        Button(onClick = onSave, modifier = Modifier.padding(top = 12.dp)) { Text("Save") }
        if (state.saved) Text("Saved", modifier = Modifier.padding(top = 8.dp))
    }
}
