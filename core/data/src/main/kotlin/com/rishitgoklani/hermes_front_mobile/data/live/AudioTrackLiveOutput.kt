package com.rishitgoklani.hermes_front_mobile.data.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.rishitgoklani.hermes_front_mobile.domain.face.MouthSource
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveAudioOut
import com.rishitgoklani.hermes_front_mobile.domain.live.PcmLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Streams the model's 24 kHz mono PCM16 speech to the speaker as it arrives. A small track buffer keeps [level] (the mouth) close to what is audible,
 * and [flush] drops queued speech immediately for barge-in. The track exists only while there is something to play.
 */
class AudioTrackLiveOutput(scope: CoroutineScope) : LiveAudioOut, MouthSource {
    private val rate = 24_000
    private companion object { const val STALL_MS = 1_500L }
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level
    override val openness: StateFlow<Float> get() = _level

    private val queuedBytes = AtomicLong(0)
    @Volatile private var epoch = 0
    @Volatile private var track: AudioTrack? = null
    private var writtenFrames = 0L
    private var baseHead = 0

    init { scope.launch(Dispatchers.IO) { pump() } }

    override fun play(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        queuedBytes.addAndGet(pcm.size.toLong()); _playing.value = true
        queue.trySend(pcm)
    }

    override fun flush() {
        epoch++
        while (true) { val b = queue.tryReceive().getOrNull() ?: break; queuedBytes.addAndGet(-b.size.toLong()) }
        track?.let { runCatching { it.pause(); it.flush(); it.play() } }
        writtenFrames = 0; baseHead = track?.playbackHeadPosition ?: 0
        _level.value = 0f; _playing.value = false
    }

    private fun ensureTrack(): AudioTrack {
        track?.let { return it }
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, rate / 5)) // ~200 ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        t.play(); track = t; writtenFrames = 0; baseHead = 0
        return t
    }

    private suspend fun pump() {
        val slice = rate * 2 * 20 / 1000 // 20 ms
        while (true) {
            val first = queue.receive()
            val my = epoch
            val t = ensureTrack()
            var chunk: ByteArray? = first
            while (chunk != null) {
                var off = 0
                while (off < chunk.size && my == epoch) {
                    val n = minOf(slice, chunk.size - off)
                    _level.value = PcmLevel.mouthOpenness(PcmLevel.rms(chunk, off, n))
                    val w = t.write(chunk, off, n, AudioTrack.WRITE_BLOCKING)
                    if (w <= 0) break
                    writtenFrames += w / 2; off += w
                }
                queuedBytes.addAndGet(-chunk.size.toLong())
                chunk = if (my == epoch) queue.tryReceive().getOrNull() else null
            }
            // Everything queued has been handed to the track; wait until it has actually sounded, then go quiet.
            // If the track stops advancing (audio focus taken, device glitch) do not stay "playing" forever: that would keep the half-duplex mic muted for good.
            var lastHead = -1; var stalledSince = 0L
            while (my == epoch && (t.playbackHeadPosition - baseHead) < writtenFrames) {
                val head = t.playbackHeadPosition; val now = System.nanoTime() / 1_000_000
                if (head != lastHead) { lastHead = head; stalledSince = now } else if (now - stalledSince > STALL_MS) break
                delay(20)
            }
            if (my == epoch && queuedBytes.get() <= 0 && queue.isEmpty) { _level.value = 0f; _playing.value = false }
        }
    }
}
