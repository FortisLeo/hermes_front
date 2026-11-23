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
)

/**
 * Drives the swipe carousel. Only the active model is requested; the next one is staged only after the active one is on screen and
 * [StagingPolicy] says both fit the memory budget. The viewer (one WebView) keeps at most those two resident.
 */
class AvatarCarouselViewModel(
    private val library: AvatarLibrary, private val active: ActiveSlotStore, private val files: AvatarFiles, private val cycle: CycleAvatar,
    private val prove: (String) -> Unit, private val snapshots: AvatarSnapshots? = null, private val lowMemory: () -> Boolean = { false },
    private val thumbDelayMs: Long = 1500, private val log: (String) -> Unit = {},
) : ViewModel() {
    private val shownKey = MutableStateFlow<String?>(null)

    val ui: StateFlow<CarouselUi> = combine(active.slot(), library.entries(), shownKey) { slot, entries, shown ->
        val entry = (slot as? AvatarSlot.Model)?.let { m -> entries.firstOrNull { it.id == m.id && it.status != AvatarStatus.BROKEN && files.modelPath(it.id) != null } }
        val req = entry?.let { ModelRequest(it.id, libraryModelUrl(it.id), it.textureCap) }
        val carousel = FaceCarousel(entries)
        val next = if (entry != null && shown == entry.id) carousel.stageTarget(slot)?.takeIf { files.modelPath(it.id) != null && StagingPolicy.shouldStage(entry, it, lowMemory()) } else null
        CarouselUi(slot, req, next?.let { ModelRequest(it.id, libraryModelUrl(it.id), it.textureCap) }, ready = req != null && shown != null, slotCount = carousel.slots.size)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CarouselUi())

    init {
        // Leaving an avatar slot destroys the WebView: nothing is shown any more.
        viewModelScope.launch { active.slot().collect { if (it !is AvatarSlot.Model) shownKey.value = null } }
    }

    fun swipeNext() { viewModelScope.launch { cycle() } }

    fun onShown(key: String, ms: Long, wasStaged: Boolean) {
        log("swap key=$key ms=$ms staged=$wasStaged")
        shownKey.value = key
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

    /** The viewer could not load a model: mark it broken and fall back to the eyes. */
    fun onError(key: String, message: String) {
        log("error key=$key $message")
        viewModelScope.launch { library.setStatus(key, AvatarStatus.BROKEN); if (active.current() == AvatarSlot.Model(key)) active.set(AvatarSlot.Eyes) }
    }
}
