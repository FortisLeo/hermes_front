package com.rishitgoklani.hermes_front_mobile.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * First-run introduction: what Deskbot is, why it asks for permissions, and whether Hermes answers.
 * Stateless: everything comes from [state]; the app owns the permission dialogs ([onRequestPermissions]).
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onRequestPermissions: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = state.progress
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Step ${p.number} of ${p.total}", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = onSkip, Modifier.heightIn(min = 48.dp)) { Text("Skip") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (p.step) {
                    OnboardingStep.Welcome -> Welcome()
                    OnboardingStep.Permissions -> Permissions(state, onRequestPermissions)
                    OnboardingStep.Connection -> Connection(state.connection, onTestConnection)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!p.isFirst) OutlinedButton(onClick = onBack, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Back") }
                Button(onClick = onNext, Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (p.isLast) "Done" else "Next") }
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)

@Composable
private fun Welcome() {
    Heading("Welcome to Deskbot")
    Text("Deskbot turns this phone into a desk robot. Hermes, the AI agent running in Termux on this same phone, is the brain. This app is the body: a face (or 3D avatar), a voice, a camera and the phone actions Hermes can use.")
    Text("The face fills the screen. Swipe in from the left edge to open the hidden menu: chat, voice, avatars, settings and debug.", style = MaterialTheme.typography.bodyMedium)
    Text("Everything stays on this phone and talks to Hermes at 127.0.0.1.", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Permissions(state: OnboardingUiState, onRequest: () -> Unit) {
    Heading("What Deskbot asks for")
    Text("These are the permissions the app already declares. You can skip any of them and grant them later in Android settings.", style = MaterialTheme.typography.bodyMedium)
    PermissionGroup.entries.forEach { g ->
        val granted = state.permissions[g] == true
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
            Text("${g.title}: ${if (granted) "allowed" else "not allowed yet"}", style = MaterialTheme.typography.titleMedium)
            Text(g.why, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (state.permissions.values.any { !it }) Button(onClick = onRequest, Modifier.heightIn(min = 48.dp)) { Text("Allow permissions") }
}

@Composable
private fun Connection(status: ConnectionStatus, onTest: () -> Unit) {
    Heading("Connect to Hermes")
    Text("Hermes must be running in Termux (see docs/SETUP.md). The URL and API key are set in Settings.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onTest, Modifier.heightIn(min = 48.dp), enabled = status != ConnectionStatus.Testing) { Text("Test connection") }
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            ConnectionStatus.Idle -> Text("Not tested yet.")
            ConnectionStatus.Testing -> { CircularProgressIndicator(Modifier.padding(4.dp)); Text("Testing…") }
            is ConnectionStatus.Ok -> Text("Hermes is online${if (status.version.isNotBlank()) " (${status.version})" else ""}.")
            is ConnectionStatus.Failed -> Text("Hermes is not reachable: ${status.reason}", color = MaterialTheme.colorScheme.error)
        }
    }
}
