package com.rishitgoklani.hermes_front_mobile.ui.face

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceState

/** Face with touch interaction (#328): tap reacts, long-press opens the quick-actions panel (#329). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceScreen(
    bot: BotState, face: FaceState, quickActions: List<QuickAction>, onAction: (QuickAction) -> Unit, onTap: () -> Unit,
    modifier: Modifier = Modifier, backgroundColor: Color = Color(0xFF05070A), mouth: Float = 0f, faceContent: (@Composable () -> Unit)? = null,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    /** Swipe right-to-left switches to the next face (eyes / avatars). Null disables the gesture. */
    onSwipeNext: (() -> Unit)? = null,
) {
    var panel by remember { mutableStateOf(false) }
    val swipe = remember { SwipeSlide() }
    Box(modifier.fillMaxSize().swipeToNext(swipe, onSwipeNext)) {
      Box(Modifier.fillMaxSize().graphicsLayer { translationX = swipe.offset.value; alpha = swipe.alpha }) {
        if (faceContent != null) Box(Modifier.fillMaxSize()) {
            faceContent()
            // Transparent layer on top: a WebView would swallow the touches, so taps and long-presses are caught here.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { panel = !panel }) })
        }
        else FaceView(bot, face, mouth = mouth, onTap = onTap, onLongPress = { panel = !panel }, backgroundColor = backgroundColor)
        overlay()
      }
        if (panel) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickActions.forEach { a -> Button(onClick = { onAction(a); panel = false }) { Text(a.label) } }
                }
                OutlinedButton(onClick = { panel = false }) { Text("Close") }
            }
        }
    }
}
