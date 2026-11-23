package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
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

/**
 * The Avatar section: a live preview with an upload button, and the OpenSourceAvatars catalog.
 * [preview] draws the current avatar; [onUpload] opens the system file picker for a .vrm.
 */
@Composable
fun AvatarLibraryScreen(
    state: AvatarLibraryState,
    preview: @Composable () -> Unit,
    avatarOn: Boolean,
    onToggleAvatar: () -> Unit,
    onUpload: () -> Unit,
    onLoad: () -> Unit,
    onSelect: (com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection) -> Unit,
    onQuery: (String) -> Unit,
    onInstall: (CatalogAvatar) -> Unit,
    thumbnail: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My avatar") })
            Tab(selected = tab == 1, onClick = { tab = 1; onLoad() }, text = { Text("Catalog") })
        }
        state.message?.let { Text(it, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        if (tab == 0) {
            Box(Modifier.weight(1f).fillMaxWidth()) { preview() }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onUpload, Modifier.weight(1f)) { Text("Upload .vrm") }
                OutlinedButton(onClick = onToggleAvatar, Modifier.weight(1f)) { Text(if (avatarOn) "Use eyes face" else "Use avatar") }
            }
        } else {
            LaunchedEffect(Unit) { onLoad() }
            Catalog(state, onSelect, onQuery, onInstall, thumbnail, onLoad)
        }
    }
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
