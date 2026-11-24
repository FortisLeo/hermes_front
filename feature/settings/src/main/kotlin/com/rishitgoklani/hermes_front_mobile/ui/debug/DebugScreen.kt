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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLogEntry
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen(
    state: DebugUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier, killActive: Boolean = false, onResetKill: () -> Unit = {}, safeMode: Boolean = false, onExitSafeMode: () -> Unit = {},
    onCopyCrash: (String) -> Unit = {}, onClearCrash: () -> Unit = {},
    onApiFilter: (ApiSource?) -> Unit = {}, onClearApiLog: () -> Unit = {}, onCopyApiLog: (String) -> Unit = {},
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
        val time = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
        val shown = state.shownApiCalls
        var expanded by remember { mutableStateOf(emptySet<Long>()) }
        var tab by remember { mutableStateOf(DebugTab.ApiCalls) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == DebugTab.ApiCalls, onClick = { tab = DebugTab.ApiCalls }, label = { Text("API calls (${state.apiCalls.size})") }, modifier = Modifier.minTarget().semantics { role = Role.Tab })
            FilterChip(selected = tab == DebugTab.ToolAudit, onClick = { tab = DebugTab.ToolAudit }, label = { Text("Tool audit") }, modifier = Modifier.minTarget().semantics { role = Role.Tab })
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (tab == DebugTab.ToolAudit) {
                items(s?.auditTail.orEmpty()) { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            } else {
                item { ApiCallsHeader(state.apiFilter, state.apiCalls.size, shown.size, onApiFilter, onClearApiLog) { onCopyApiLog(ApiLogFormatText.of(shown) { time.format(Date(it)) }) } }
                if (shown.isEmpty()) item { Text(if (state.apiCalls.isEmpty()) "No API calls yet." else "No calls from this source.", style = MaterialTheme.typography.bodySmall) }
                items(shown, key = { it.id }) { e -> ApiCallRow(e, time.format(Date(e.startedAt)), e.id in expanded) { expanded = if (e.id in expanded) expanded - e.id else expanded + e.id } }
            }
        }
    }
}

private enum class DebugTab { ApiCalls, ToolAudit }

/** Plain-text export of the visible API calls (same text the Copy button puts on the clipboard). */
internal object ApiLogFormatText {
    fun of(entries: List<ApiLogEntry>, timeText: (Long) -> String): String = com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLogFormat.all(entries, timeText)
}

/** "API calls": filter by source, copy what is shown, or clear. */
@Composable
internal fun ApiCallsHeader(filter: ApiSource?, total: Int, shownCount: Int, onFilter: (ApiSource?) -> Unit, onClear: () -> Unit, onCopy: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text("API calls ($shownCount${if (shownCount != total) " of $total" else ""})", Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
        Text("Newest first. Kept in memory only; keys and tokens are masked and large payloads are collapsed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All") }, modifier = Modifier.minTarget().semantics { role = Role.RadioButton })
            ApiSource.entries.forEach { src -> FilterChip(selected = filter == src, onClick = { onFilter(src) }, label = { Text(src.label) }, modifier = Modifier.minTarget().semantics { role = Role.RadioButton }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy, Modifier.minTarget()) { Text("Copy shown") }
            OutlinedButton(onClick = onClear, Modifier.minTarget()) { Text("Clear log") }
        }
    }
}

/** One exchange: a summary line that opens to the full request and response. */
@Composable
internal fun ApiCallRow(e: ApiLogEntry, time: String, expanded: Boolean, onToggle: () -> Unit) {
    val mark = when (e.status) { ApiStatus.Pending -> "…"; ApiStatus.Ok -> "OK"; ApiStatus.Error -> "FAIL" }
    val color = if (e.status == ApiStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Collapse" else "Expand", role = Role.Button, onClick = onToggle).minTarget().semantics { stateDescription = if (expanded) "expanded" else "collapsed" }) {
        Text("$time  ${e.source.label}  $mark${e.durationMs?.let { "  $it ms" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = color)
        Text(e.label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = if (expanded) Int.MAX_VALUE else 1)
        if (expanded) {
            Text("Request", style = MaterialTheme.typography.labelSmall)
            Text(e.request.ifBlank { "(empty)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("Response", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
            Text(e.response.ifBlank { "(none yet)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        } else if (e.response.isNotBlank()) Text(e.response.replace('\n', ' '), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
