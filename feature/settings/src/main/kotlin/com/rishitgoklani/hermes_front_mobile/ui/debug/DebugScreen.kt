package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(state: DebugUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier, killActive: Boolean = false, onResetKill: () -> Unit = {}, safeMode: Boolean = false, onExitSafeMode: () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val s = state.snapshot
        Text("Hermes: " + (if (s == null) "…" else if (s.hermesHealthy) "online ${s.hermesVersion}" else "UNREACHABLE"), style = MaterialTheme.typography.titleMedium)
        Text("Bridge tools: ${s?.bridgeTools ?: "…"}")
        s?.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (safeMode) { Text("SAFE MODE: started minimal after repeated crashes", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium); Button(onClick = onExitSafeMode) { Text("Exit safe mode") } }
        if (killActive) { Text("KILL SWITCH ACTIVE: tools are blocked", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium); Button(onClick = onResetKill) { Text("Reset kill switch") } }
        Button(onClick = onRefresh) { Text(if (state.loading) "Refreshing…" else "Refresh") }
        Text("Last tool calls", style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(s?.auditTail.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
    }
}
