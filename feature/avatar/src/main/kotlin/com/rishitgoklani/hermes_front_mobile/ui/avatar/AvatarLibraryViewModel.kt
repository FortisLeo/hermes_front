package com.rishitgoklani.hermes_front_mobile.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarCatalogSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarInstaller
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
    /** One-shot result line for the user ("Using 'Devil'", "Model rejected: …"). */
    val message: String? = null,
) {
    val shown: List<CatalogAvatar> get() = OsaCatalog.search(avatars, query)
}

/** Browse the OpenSourceAvatars catalog and install a model, either from the catalog or from a picked file. */
class AvatarLibraryViewModel(private val source: AvatarCatalogSource, private val installer: AvatarInstaller) : ViewModel() {
    private val _state = MutableStateFlow(AvatarLibraryState())
    val state: StateFlow<AvatarLibraryState> = _state
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
            val msg = try { report(a.name, installer.install(source.download(a.modelUrl))) }
            catch (e: CancellationException) { throw e } catch (e: Exception) { "Download failed: ${e.message ?: "no network"}" }
            _state.update { it.copy(installingId = null, message = msg) }
        }
    }

    /** A file the user picked on the phone. [bytes] is null when it could not be read. */
    fun importFile(name: String, bytes: ByteArray?) {
        if (bytes == null) { _state.update { it.copy(message = "Could not read $name") }; return }
        if (bytes.size > VrmLicense.MAX_BYTES) { _state.update { it.copy(message = "Model rejected: larger than 50 MB") }; return }
        viewModelScope.launch { val msg = report(name, installer.install(bytes)); _state.update { it.copy(message = msg) } }
    }

    /** Preview image bytes for a catalog entry, cached in memory. */
    suspend fun thumbnail(url: String): ByteArray? {
        if (url.isEmpty()) return null
        synchronized(thumbs) { if (thumbs.containsKey(url)) return thumbs[url] }
        val b = source.thumbnail(url)
        if (b != null) synchronized(thumbs) { thumbs[url] = b }
        return b
    }

    private fun report(name: String, r: InstallResult) = when (r) {
        is InstallResult.Installed -> "Using '${r.info.title.ifEmpty { name }}' (${r.info.license})"
        is InstallResult.Rejected -> "Model rejected: ${r.reason}"
    }
}
