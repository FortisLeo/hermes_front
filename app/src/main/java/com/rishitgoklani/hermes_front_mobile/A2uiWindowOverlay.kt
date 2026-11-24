package com.rishitgoklani.hermes_front_mobile

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Screen
import com.rishitgoklani.hermes_front_mobile.domain.overlay.OverlayPort
import com.rishitgoklani.hermes_front_mobile.ui.a2ui.A2uiHost
import com.rishitgoklani.hermes_front_mobile.ui.a2ui.StatusProvider
import com.rishitgoklani.hermes_front_mobile.ui.theme.Hermes_front_mobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lifecycle, saved-state and view-model owner for a ComposeView that lives in a WindowManager window instead of an Activity. */
private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
}

/**
 * OverlayPort implementation (#414): the A2UI overlay screen drawn in a TYPE_APPLICATION_OVERLAY window above other apps.
 * The window is bottom-anchored and wraps its content, FLAG_NOT_FOCUSABLE keeps the keyboard and back button with the app below,
 * FLAG_NOT_TOUCH_MODAL lets touches outside the card reach that app, and the card has its own close button.
 */
class A2uiWindowOverlay(private val context: Context, private val store: A2uiStore, private val status: StatusProvider, private val onAction: (String) -> Unit) : OverlayPort {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val content = mutableStateOf<Screen?>(null)
    private val dismissHandler = mutableStateOf({})
    private var view: ComposeView? = null
    private var owner: WindowOwner? = null

    override fun isAllowed(): Boolean = Settings.canDrawOverlays(context)

    override suspend fun show(screen: Screen, onDismiss: () -> Unit) = withContext(Dispatchers.Main) {
        content.value = screen; dismissHandler.value = onDismiss
        if (view != null) return@withContext
        val o = WindowOwner().also { it.resume() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o); setViewTreeViewModelStoreOwner(o)
            setContent {
                val s = content.value; val values by store.values.collectAsState()
                if (s != null) Hermes_front_mobileTheme {
                    Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { dismissHandler.value() }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Dismiss this card" }) { Text("Close") }
                            }
                            A2uiHost(s.root, values, store::setValue, onAction, status, Modifier.heightIn(max = 420.dp))
                        }
                    }
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        wm.addView(v, lp); view = v; owner = o
    }

    override suspend fun hide() = withContext(Dispatchers.Main) {
        view?.let { runCatching { wm.removeView(it) } }; view = null
        owner?.destroy(); owner = null; content.value = null
    }
}
