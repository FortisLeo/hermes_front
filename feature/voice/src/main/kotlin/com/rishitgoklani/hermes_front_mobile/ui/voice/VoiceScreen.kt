package com.rishitgoklani.hermes_front_mobile.ui.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VoiceScreen(
    state: VoiceUiState, onTalk: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier,
    wakeEnabled: Boolean = false, wakeStatus: String = "", onWakeToggle: (Boolean) -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("State: ${state.bot}", style = MaterialTheme.typography.titleMedium)
        if (state.heard.isNotEmpty()) Text("You: ${state.heard}")
        if (state.reply.isNotEmpty()) Text("Bot: ${state.reply}")
        if (state.timing.isNotEmpty()) Text(state.timing, style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Switch(wakeEnabled, onWakeToggle); Text("Always listening (\"hey bot\")")
        }
        if (wakeStatus.isNotBlank()) Text(wakeStatus, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onTalk) { Text("Talk") }
        Button(onClick = onStop) { Text("Stop") }
    }
}
