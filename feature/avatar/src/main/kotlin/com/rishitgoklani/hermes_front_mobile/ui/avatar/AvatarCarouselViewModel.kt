package com.rishitgoklani.hermes_front_mobile.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarFiles
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarLibrary
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.CycleAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.FaceCarousel
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.MemoryNativeGlSwitch
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.NativeGlSwitch
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.StagingPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the face screen needs: the slot, the model the viewer should show, and the one it may stage behind it. */
data class CarouselUi(
    val slot: AvatarSlot = AvatarSlot.Eyes,
    val active: ModelRequest? = null,
    val staged: ModelRequest? = null,
    /** The viewer has a model on screen, so the eyes cover can go away. */
    val ready: Boolean = false,
    /** How many slots a swipe walks through (Eyes + usable avatars). */
    val slotCount: Int = 1,
    /** Native 3D was switched off because the GPU path crashed or kept failing; the face stays on Eyes until the user retries. */
    val nativeDisabled: Boolean = false,
)

/**
 * Drives the swipe carousel. Only the active model is requested; the next one is staged only after the active one is on screen and
 * [StagingPolicy] says both fit the memory budget. The viewer (one WebView) keeps at most those two resident.
 */
class AvatarCarouselViewModel(
    private val library: AvatarLibrary, private val active: ActiveSlotStore, private val files: AvatarFiles, private val cycle: CycleAvatar,
    private val prove: (String) -> Unit, private val snapshots: AvatarSnapshots? = null, private val lowMemory: () -> Boolean = { false },
    private val thumbDelayMs: Long = 1500, private val log: (String) -> Unit = {},
    private val nativeGl: NativeGlSwitch = MemoryNativeGlSwitch(), private val maxConsecutiveErrors: Int = 3,
) : ViewModel() {
    private val shownKey = MutableStateFlow<String?>(null)
    /** null until the persisted flag has been read: nothing is requested from the GPU before that. */
    private val nativeOff = MutableStateFlow<Boolean?>(null)
    private var consecutiveErrors = 0
    private var fatalKey: String? = null

    val ui: StateFlow<CarouselUi> = combine(active.slot(), library.entries(), shownKey, nativeOff) { slot, entries, shown, off ->
        val entry = (slot as? AvatarSlot.Model)?.takeIf { off == false }?.let { m -> entries.firstOrNull { it.id == m.id && it.status != AvatarStatus.BROKEN && files.modelPath(it.id) != null } }
        val req = entry?.let { ModelRequest(it.id, libraryModelUrl(it.id), it.textureCap) }
        val carousel = FaceCarousel(entries)
        val next = if (entry != null && shown == entry.id) carousel.stageTarget(slot)?.takeIf { files.modelPath(it.id) != null && StagingPolicy.shouldStage(entry, it, lowMemory()) } else null
        CarouselUi(slot, req, next?.let { ModelRequest(it.id, libraryModelUrl(it.id), it.textureCap) }, ready = req != null && shown != null, slotCount = carousel.slots.size, nativeDisabled = off == true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CarouselUi())

    init {
        viewModelScope.launch { nativeOff.value = nativeGl.disabled() }
        // Leaving an avatar slot destroys the WebView: nothing is shown any more.
        viewModelScope.launch { active.slot().collect { if (it !is AvatarSlot.Model) shownKey.value = null } }
    }

    fun swipeNext() { viewModelScope.launch { cycle() } }

    fun onShown(key: String, ms: Long, wasStaged: Boolean) {
        log("swap key=$key ms=$ms staged=$wasStaged")
        shownKey.value = key; consecutiveErrors = 0
        prove(key)
        viewModelScope.launch {
            val e = library.get(key) ?: return@launch
            if (e.thumbPath != null || snapshots == null) return@launch
            delay(thumbDelayMs)
            if (active.current() != AvatarSlot.Model(key)) return@launch
            snapshots.take()?.let { library.setThumb(key, files.writeThumb(key, it)) }
        }
    }

    fun onStaged(key: String, ms: Long) { log("staged key=$key ms=$ms") }

    /** The viewer could not load a model: mark it broken and fall back to the eyes. Repeated failures switch native 3D off. */
    fun onError(key: String, message: String) {
        log("error key=$key $message")
        val tooMany = ++consecutiveErrors >= maxConsecutiveErrors
        if (tooMany) nativeOff.value = true
        viewModelScope.launch {
            library.setStatus(key, AvatarStatus.BROKEN); if (active.current() == AvatarSlot.Model(key)) active.set(AvatarSlot.Eyes)
            if (tooMany) nativeGl.setDisabled(true)
        }
    }

    /** The renderer itself failed (shader, EGL, draw): the shown model is marked broken, the face falls back to the eyes, and native 3D stays off on next launch. */
    fun onFatal(message: String) {
        log("fatal $message")
        nativeOff.value = true   // synchronously: no request may reach the GPU again
        viewModelScope.launch {
            val key = shownKey.value ?: (active.current() as? AvatarSlot.Model)?.id
            fatalKey = key
            key?.let { library.setStatus(it, AvatarStatus.BROKEN) }
            active.set(AvatarSlot.Eyes); shownKey.value = null
            nativeGl.setDisabled(true)
        }
    }

    /** The user pressed Retry after native 3D was switched off. */
    fun retryNative() {
        consecutiveErrors = 0; nativeOff.value = false
        viewModelScope.launch { nativeGl.setDisabled(false); fatalKey?.let { library.setStatus(it, AvatarStatus.OK) }; fatalKey = null }
    }
}
