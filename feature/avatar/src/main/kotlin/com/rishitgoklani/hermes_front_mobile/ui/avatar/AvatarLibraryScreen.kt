package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus

/**
 * The Avatar section: the library of installed (optimized) avatars with upload/remove, and the OpenSourceAvatars catalog.
 * Stateless: everything comes from [state]; actions go out through the callbacks.
 */
@Composable
fun AvatarLibraryScreen(
    state: AvatarLibraryState,
    onUse: (AvatarSlot) -> Unit,
    onRetry: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUpload: () -> Unit,
    onLoad: () -> Unit,
    onSelect: (com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection) -> Unit,
    onQuery: (String) -> Unit,
    onInstall: (CatalogAvatar) -> Unit,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var confirmRemove by remember { mutableStateOf<AvatarEntry?>(null) }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Library (${state.installed.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1; onLoad() }, text = { Text("Catalog") })
        }
        state.message?.let { Text(it, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        state.progress?.let { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (p.total > 0) "${p.stage} ${p.done}/${p.total}" else p.stage, style = MaterialTheme.typography.bodySmall)
                if (p.total > 0) LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (tab == 0) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onUpload, Modifier.weight(1f), enabled = state.installingId == null) { Text("Upload .vrm") }
                OutlinedButton(onClick = { onUse(AvatarSlot.Eyes) }, Modifier.weight(1f), enabled = state.activeSlot != AvatarSlot.Eyes) { Text("Use eyes face") }
            }
            Text("Swipe right to left on the face to switch between the eyes and your avatars.", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.installed.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Text("No avatars yet. Upload a .vrm or pick one from the Catalog tab.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.installed, key = { it.id }) { e -> InstalledCard(e, active = state.activeSlot == AvatarSlot.Model(e.id), onUse = { if (e.status == AvatarStatus.BROKEN) onRetry(e.id) else onUse(AvatarSlot.Model(e.id)) }, onRemove = { confirmRemove = e }) }
            }
        } else {
            LaunchedEffect(Unit) { onLoad() }
            Catalog(state, onSelect, onQuery, onInstall, thumbnail, onLoad)
        }
    }
    confirmRemove?.let { e ->
        AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text("Remove ${e.name}?") }, text = { Text("The optimized model and its preview are deleted from this phone. You can add it again later.") },
            confirmButton = { TextButton(onClick = { onRemove(e.id); confirmRemove = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } })
    }
}

@Composable
private fun InstalledCard(e: AvatarEntry, active: Boolean, onUse: () -> Unit, onRemove: () -> Unit) {
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.thumbPath) {
        value = e.thumbPath?.let { p -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(p)?.asImageBitmap() }.getOrNull() } }
    }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF11161C)).clickable(onClick = onUse), Alignment.Center) {
            thumb?.let { Image(it, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text(e.name.take(1).uppercase(), color = Color.Gray, style = MaterialTheme.typography.headlineLarge)
        }
        Text(e.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(statusLine(e, active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onUse, enabled = !active, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (active) "Showing" else if (e.status == AvatarStatus.BROKEN) "Retry" else "Use") }
            TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Remove", maxLines = 1) }
        }
    }
}

private fun mb(b: Long) = "%.1f MB".format(b / 1048576.0)
internal fun statusLine(e: AvatarEntry, active: Boolean): String = when {
    e.status == AvatarStatus.BROKEN -> "The app stopped while loading this one. Retry or remove."
    else -> "${mb(e.storedBytes)}${if (e.storedBytes < e.originalBytes) " (was ${mb(e.originalBytes)})" else ""} · ${e.textureCap}px${if (active) " · showing" else ""}"
}

@Composable
private fun Catalog(state: AvatarLibraryState, onSelect: (com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection) -> Unit, onQuery: (String) -> Unit, onInstall: (CatalogAvatar) -> Unit, thumbnail: suspend (String) -> ByteArray?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.query, onQuery, Modifier.width(180.dp), singleLine = true, placeholder = { Text("Search") }, textStyle = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.collections, key = { it.id }) { c -> FilterChip(selected = state.collection?.id == c.id, onClick = { onSelect(c) }, label = { Text("${c.name} · ${c.license}") }) }
            }
        }
        when {
            state.loading && state.avatars.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.collections.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { OutlinedButton(onClick = onRetry) { Text("Retry") } }
            else -> LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.shown, key = { it.id }) { a -> Card(a, installing = state.installingId == a.id, enabled = state.installingId == null, onClick = { onInstall(a) }, thumbnail = thumbnail) }
            }
        }
    }
}

@Composable
private fun Card(a: CatalogAvatar, installing: Boolean, enabled: Boolean, onClick: () -> Unit, thumbnail: suspend (String) -> ByteArray?) {
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.thumbUrl) {
        value = thumbnail(a.thumbUrl)?.let { b -> runCatching { BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull() }
    }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = enabled, onClick = onClick).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF11161C)), Alignment.Center) {
            img?.let { Image(it, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text(a.name.take(1).uppercase(), color = Color.Gray, style = MaterialTheme.typography.headlineLarge)
            if (installing) CircularProgressIndicator()
        }
        Text(a.name, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(a.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
