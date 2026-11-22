package com.rishitgoklani.hermes_front_mobile.ui.a2ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.DeviceToggles
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Live number for a StatusGauge (e.g. battery percent); null while unknown. */
fun interface StatusProvider { suspend fun read(kind: String): Pair<Float, String>? }

/** Renders a validated component tree with native Compose widgets. Bound controls update [onValue] locally; only Buttons call [onAction]. */
@Composable
fun A2uiHost(
    root: JsonObject, values: Map<String, JsonElement>, onValue: (String, JsonElement) -> Unit, onAction: (String) -> Unit,
    status: StatusProvider, modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp)) { Node(root, values, onValue, onAction, status) }
}

@Composable
private fun Node(n: JsonObject, values: Map<String, JsonElement>, onValue: (String, JsonElement) -> Unit, onAction: (String) -> Unit, status: StatusProvider) {
    val kids: @Composable () -> Unit = { n.kids().forEach { Node(it, values, onValue, onAction, status) } }
    when (n.str("type")) {
        "Text" -> Text(n.str("text"), style = when (n.str("style")) { "title" -> MaterialTheme.typography.titleLarge; "caption" -> MaterialTheme.typography.bodySmall; else -> MaterialTheme.typography.bodyLarge }, modifier = Modifier.padding(vertical = 2.dp))
        "Card" -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) { kids() } }
        "Row" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            n.kids().forEach { k -> androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { Node(k, values, onValue, onAction, status) } }
        }
        "Column" -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) { kids() }
        "Divider" -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
        "Button" -> Button(onClick = { onAction(n.str("action")) }) { Text(n.str("label")) }
        "Toggle" -> {
            val bind = n.str("bind"); val on = (values[bind] as? JsonPrimitive)?.booleanOrNull ?: n.flag("default")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(on, { onValue(bind, JsonPrimitive(it)) }); Text(n.str("label")) }
        }
        "Slider" -> {
            val bind = n.str("bind"); val min = n.num("min", 0f); val max = n.num("max", 100f).coerceAtLeast(min + 1f)
            val v = ((values[bind] as? JsonPrimitive)?.content?.toFloatOrNull() ?: n.num("default", min)).coerceIn(min, max)
            Column { Text("${n.str("label")}: ${v.toInt()}"); Slider(v, { onValue(bind, JsonPrimitive(it)) }, valueRange = min..max) }
        }
        "Gauge" -> { val f = fraction(n.num("value"), n.num("max", 100f)); Column(Modifier.padding(vertical = 4.dp)) { Text("${n.str("label")}: ${n.num("value").toInt()}"); LinearProgressIndicator(progress = { f }, Modifier.fillMaxWidth()) } }
        "StatusGauge" -> {
            val kind = n.str("kind"); var reading by remember { mutableStateOf<Pair<Float, String>?>(null) }
            LaunchedEffect(kind) { while (true) { reading = runCatching { status.read(kind) }.getOrNull(); delay(5000) } }
            Column(Modifier.padding(vertical = 4.dp)) { Text("${kind.replaceFirstChar { it.uppercase() }}: ${reading?.second ?: "…"}"); LinearProgressIndicator(progress = { fraction(reading?.first ?: 0f, 100f) }, Modifier.fillMaxWidth()) }
        }
        "Timer" -> {
            val ends = n.num("ends_at_ms", 0f).toLong(); var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(ends) { while (true) { now = System.currentTimeMillis(); delay(500) } }
            Column { Text(n.str("label"), style = MaterialTheme.typography.titleMedium); Text(if (ends > 0) formatRemaining(ends - now) else "--:--", style = MaterialTheme.typography.displaySmall) }
        }
        "ConfirmCard" -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(n.str("text"), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ onAction(n.str("yes")) }) { Text(n.str("yes_label", "Yes")) }; OutlinedButton({ onAction(n.str("no")) }) { Text(n.str("no_label", "No")) } }
            }
        }
        "AppTile" -> OutlinedButton({ onAction("""tool:app_open {"name":"${n.str("package").replace("\"", "")}"}""") }) { Text(n.str("label")) }
        "NotificationCard" -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) { Text(n.str("app"), style = MaterialTheme.typography.labelMedium); Text(n.str("title"), style = MaterialTheme.typography.titleMedium); Text(n.str("text")) } }
        "SkillCard" -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Column(Modifier.padding(12.dp)) { Text(n.str("name"), style = MaterialTheme.typography.titleMedium); Text(n.str("description")) } }
        "DeviceToggle" -> {
            val device = n.str("device"); val bind = "device:$device"; val on = (values[bind] as? JsonPrimitive)?.booleanOrNull ?: false
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(on, { v -> DeviceToggles.action(device, v)?.let { onValue(bind, JsonPrimitive(v)); onAction(it) } }); Text(n.str("label", device.replaceFirstChar { it.uppercase() }))
            }
        }
        "Image" -> {
            val bmp = remember(n.str("base64")) { runCatching { val b = android.util.Base64.decode(n.str("base64"), android.util.Base64.DEFAULT); BitmapFactory.decodeByteArray(b, 0, b.size) }.getOrNull() }
            if (bmp != null) Image(bmp.asImageBitmap(), n.str("desc"), Modifier.fillMaxWidth()) else Text("(image unavailable)")
        }
        "WebEmbed" -> SandboxedWeb(n.str("html"), n.flag("js"), n.num("height", 300f).coerceIn(80f, 900f))
        "PersonaView" -> Text("(persona preview shows on the Face tab)", style = MaterialTheme.typography.bodySmall)
        else -> Text("Unsupported component", color = MaterialTheme.colorScheme.error)
    }
}


/** Sandboxed HTML: no network, no file/content access, no bridge to the app; JavaScript only if the screen asks for it. */
@androidx.compose.runtime.Composable
private fun SandboxedWeb(html: String, js: Boolean, heightDp: Float) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = js; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = true; settings.setGeolocationEnabled(false)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? =
                        if (request.url.scheme == "data" || request.url.scheme == "about") null else android.webkit.WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}
