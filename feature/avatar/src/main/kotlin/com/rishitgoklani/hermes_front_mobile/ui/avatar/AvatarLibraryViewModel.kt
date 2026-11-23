package com.rishitgoklani.hermes_front_mobile.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarCatalogSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.ModelInstaller
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarLibrary
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ImportProgress
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.RemoveAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.SelectAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection
import com.rishitgoklani.hermes_front_mobile.domain.avatar.InstallResult
import com.rishitgoklani.hermes_front_mobile.domain.avatar.OsaCatalog
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmLicense
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AvatarLibraryState(
    val collections: List<CatalogCollection> = emptyList(),
    val collection: CatalogCollection? = null,
    val query: String = "",
    val avatars: List<CatalogAvatar> = emptyList(),
    val loading: Boolean = false,
    val installingId: String? = null,
    /** Installed avatars (from Room) and the slot being shown. */
    val installed: List<AvatarEntry> = emptyList(),
    val activeSlot: AvatarSlot = AvatarSlot.Eyes,
    /** Import/optimize progress while a model is being added. */
    val progress: ImportProgress? = null,
    /** One-shot result line for the user ("Using 'Devil'", "Model rejected: …"). */
    val message: String? = null,
) {
    val shown: List<CatalogAvatar> get() = OsaCatalog.search(avatars, query)
}

/** Browse the OpenSourceAvatars catalog and install a model, either from the catalog or from a picked file. */
class AvatarLibraryViewModel(
    private val source: AvatarCatalogSource, private val installer: ModelInstaller, private val library: AvatarLibrary, private val active: ActiveSlotStore,
    private val select: SelectAvatar, private val remove: RemoveAvatar,
) : ViewModel() {
    private val _state = MutableStateFlow(AvatarLibraryState())
    val state: StateFlow<AvatarLibraryState> = _state
    init {
        viewModelScope.launch { library.entries().collect { list -> _state.update { it.copy(installed = list) } } }
        viewModelScope.launch { active.slot().collect { slot -> _state.update { it.copy(activeSlot = slot) } } }
    }
    private val thumbs = object : LinkedHashMap<String, ByteArray?>(64, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ByteArray?>) = size > 80 }

    /** Loads the collection list (and the first collection) once; call again to retry after an error. */
    fun load() {
        if (_state.value.loading || _state.value.collections.isNotEmpty()) return
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                val cols = source.collections()
                _state.update { it.copy(collections = cols, loading = false, message = if (cols.isEmpty()) "Catalog is empty" else null) }
                cols.firstOrNull()?.let { select(it) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loading = false, message = "Catalog unavailable: ${e.message ?: "no network"}") } }
        }
    }

    fun select(c: CatalogCollection) {
        _state.update { it.copy(collection = c, avatars = emptyList(), loading = true, message = null) }
        viewModelScope.launch {
            try { val list = source.avatars(c); _state.update { if (it.collection == c) it.copy(avatars = list, loading = false) else it } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(loading = false, message = "Could not load ${c.name}: ${e.message ?: "no network"}") } }
        }
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun install(a: CatalogAvatar) {
        if (_state.value.installingId != null) return
        _state.update { it.copy(installingId = a.id, message = null) }
        viewModelScope.launch {
            val msg = try { report(a.name, installer.install(source.download(a.modelUrl), true, a.name, AvatarSource.CATALOG, ::onProgress)) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { "Download failed: ${e.message ?: "no network"}" }
            _state.update { it.copy(installingId = null, progress = null, message = msg) }
        }
    }

    /** A file the user picked on the phone. [bytes] is null when it could not be read. */
    fun importFile(name: String, bytes: ByteArray?) {
        if (bytes == null) { _state.update { it.copy(message = "Could not read $name") }; return }
        if (bytes.size > VrmLicense.MAX_BYTES) { _state.update { it.copy(message = "Model rejected: larger than 50 MB") }; return }
        viewModelScope.launch {
            _state.update { it.copy(installingId = IMPORT_ID) }
            val msg = try { report(name, installer.install(bytes, false, name.removeSuffix(".vrm").removeSuffix(".VRM"), AvatarSource.UPLOAD, ::onProgress)) } catch (e: CancellationException) { throw e } catch (e: Exception) { "Import failed: ${e.message ?: "unknown error"}" }
            _state.update { it.copy(installingId = null, progress = null, message = msg) }
        }
    }

    /** Preview image bytes for a catalog entry, cached in memory. */
    suspend fun thumbnail(url: String): ByteArray? {
        if (url.isEmpty()) return null
        synchronized(thumbs) { if (thumbs.containsKey(url)) return thumbs[url] }
        val b = source.thumbnail(url)
        if (b != null) synchronized(thumbs) { thumbs[url] = b }
        return b
    }

    private fun onProgress(p: ImportProgress) { _state.update { it.copy(progress = p) } }

    /** Show this slot (an installed avatar, or the eyes). */
    fun use(slot: AvatarSlot) { viewModelScope.launch { select(slot) } }
    /** A model marked BROKEN (the app died while it was loading) can be tried again: it becomes PENDING and is shown; if the app dies again it is marked BROKEN again. */
    fun retry(id: String) { viewModelScope.launch { library.setStatus(id, com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus.PENDING); select(AvatarSlot.Model(id)) } }
    fun removeAvatar(id: String) { viewModelScope.launch { remove(id) } }
    fun rename(id: String, name: String) { viewModelScope.launch { library.rename(id, name) } }

    private fun report(name: String, r: InstallResult) = when (r) {
        is InstallResult.Installed -> "Using '${r.info.title.ifEmpty { name }}' (${r.info.license})"
        is InstallResult.Rejected -> "Model rejected: ${r.reason}"
    }

    private companion object { const val IMPORT_ID = "__import__" }
}
