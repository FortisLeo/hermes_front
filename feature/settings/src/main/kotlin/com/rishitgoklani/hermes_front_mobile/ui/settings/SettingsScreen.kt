package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onDisplayQuality: (DisplayQuality) -> Unit = {},
    onShowIntro: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(state.baseUrl, onBaseUrl, Modifier.fillMaxWidth(), label = { Text("Hermes URL") })
        OutlinedTextField(
            state.apiKey, onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        Button(onClick = onSave, modifier = Modifier.padding(top = 12.dp).minTarget()) { Text("Save") }
        if (state.saved) Text("Saved", modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })

        Text("Display quality", Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text("How much detail the 3D avatar may use. Auto picks from the phone; Low saves battery and memory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayQuality.entries.forEach { q ->
                FilterChip(selected = state.displayQuality == q, onClick = { onDisplayQuality(q) }, label = { Text(q.label) }, modifier = Modifier.minTarget().semantics { role = Role.RadioButton })
            }
        }

        Text("Introduction", Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onShowIntro, Modifier.padding(top = 8.dp).minTarget()) { Text("Show intro again") }

        Text("About", Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        AboutBlock(state.about)
    }
}

@Composable
internal fun AboutBlock(about: AboutInfo) {
    Column(Modifier.padding(top = 8.dp).semantics(mergeDescendants = true) {}) {
        Text("Deskbot ${about.versionName}".trim())
        if (about.buildType.isNotBlank()) Text("Build: ${about.buildType}", style = MaterialTheme.typography.bodySmall)
        Text("Setup and troubleshooting: ${about.docs}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
