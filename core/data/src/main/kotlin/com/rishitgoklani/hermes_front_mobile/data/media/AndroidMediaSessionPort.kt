package com.rishitgoklani.hermes_front_mobile.data.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.rishitgoklani.hermes_front_mobile.data.notifications.DeskbotNotificationListener
import com.rishitgoklani.hermes_front_mobile.domain.media.MediaSessionPort
import com.rishitgoklani.hermes_front_mobile.domain.media.NowPlaying

/** Reads active media sessions; allowed because our notification listener is enabled (that is the access MediaSessionManager checks). */
class AndroidMediaSessionPort(private val context: Context) : MediaSessionPort {
    override suspend fun active(): List<NowPlaying> {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val controllers = try { manager.getActiveSessions(ComponentName(context, DeskbotNotificationListener::class.java)) }
        catch (e: SecurityException) { throw IllegalStateException("Notification access not enabled") }
        return controllers.map { c ->
            val m = c.metadata
            val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
            NowPlaying(
                c.packageName, label, m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                (m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(), m?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }
    }
}
