package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(
    state: DebugUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier, killActive: Boolean = false, onResetKill: () -> Unit = {}, safeMode: Boolean = false, onExitSafeMode: () -> Unit = {},
    onCopyCrash: (String) -> Unit = {}, onClearCrash: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val s = state.snapshot
        // Live region: a change between online / unreachable is announced by screen readers.
        Text("Hermes: " + (if (s == null) "…" else if (s.hermesHealthy) "online ${s.hermesVersion}" else "UNREACHABLE"), Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleMedium)
        Text("Bridge tools: ${s?.bridgeTools ?: "…"}")
        s?.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (safeMode) { Text("SAFE MODE: started minimal after repeated crashes", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium); Button(onClick = onExitSafeMode, Modifier.minTarget()) { Text("Exit safe mode") } }
        if (killActive) { Text("KILL SWITCH ACTIVE: tools are blocked", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium); Button(onClick = onResetKill, Modifier.minTarget()) { Text("Reset kill switch") } }
        Button(onClick = onRefresh, Modifier.minTarget()) { Text(if (state.loading) "Refreshing…" else "Refresh") }
        CrashSection(state.lastCrash, onCopyCrash, onClearCrash)
        Text("Last tool calls", Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(s?.auditTail.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
    }
}

/** "Last crash": the newest recorded uncaught exception with Copy and Clear. Shows a plain line when there is none. */
@Composable
internal fun CrashSection(crash: String?, onCopy: (String) -> Unit, onClear: () -> Unit) {
    Text("Last crash", Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
    if (crash == null) { Text("No crash recorded.", style = MaterialTheme.typography.bodySmall); return }
    Text(crash, Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onCopy(crash) }, Modifier.minTarget()) { Text("Copy crash report") }
        OutlinedButton(onClick = onClear, Modifier.minTarget()) { Text("Clear crash report") }
    }
}

/** Material's minimum touch target (48dp). */
internal fun Modifier.minTarget(): Modifier = this.heightIn(min = 48.dp)
