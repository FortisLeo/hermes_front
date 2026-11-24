package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveVoices

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    onDisplayQuality: (DisplayQuality) -> Unit = {},
    onShowIntro: () -> Unit = {},
    live: LiveSettingsUiState? = null,
    liveActions: LiveSettingsActions = LiveSettingsActions(),
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(state.baseUrl, onBaseUrl, Modifier.fillMaxWidth(), label = { Text("Hermes URL") })
        OutlinedTextField(
            state.apiKey, onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        Button(onClick = onSave, modifier = Modifier.padding(top = 12.dp).minTarget()) { Text("Save") }
        if (state.saved) Text("Saved", modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })

        if (live != null) LiveSettingsSection(live, liveActions)

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

class LiveSettingsActions(
    val onEnabled: (Boolean) -> Unit = {}, val onApiKey: (String) -> Unit = {}, val onModel: (String) -> Unit = {},
    val onVoice: (String) -> Unit = {}, val onHalfDuplex: (Boolean) -> Unit = {}, val onSave: () -> Unit = {}, val onAllTools: (Boolean) -> Unit = {},
)

@Composable
internal fun LiveSettingsSection(state: LiveSettingsUiState, a: LiveSettingsActions) {
    val s = state.settings
    Text("Gemini Live voice", Modifier.padding(top = 24.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    Text("Talk straight to Gemini over one streaming connection instead of going through Hermes. Faster to first word. Needs a Google AI Studio key; audio is sent to Google.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Use Gemini Live")
        Switch(checked = s.enabled, onCheckedChange = a.onEnabled)
    }
    OutlinedTextField(s.apiKey, a.onApiKey, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
    OutlinedTextField(s.model, a.onModel, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Model") }, singleLine = true)
    Text("Voice", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveVoices.ALL.forEach { v -> FilterChip(selected = s.voice == v, onClick = { a.onVoice(v) }, label = { Text(v) }, modifier = Modifier.minTarget().semantics { role = Role.RadioButton }) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text("Mute mic while it talks"); Text("Stops it hearing itself. Turn off to interrupt it by speaking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = s.halfDuplex, onCheckedChange = a.onHalfDuplex)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text("All phone tools and skills"); Text("Lets Gemini use every tool and skill guide Hermes has. Turn off for a lean set of about 35 tools.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = s.allTools, onCheckedChange = a.onAllTools)
    }
    Button(onClick = a.onSave, modifier = Modifier.padding(top = 12.dp).minTarget()) { Text("Save Gemini settings") }
    if (state.saved) Text("Saved", modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
}
