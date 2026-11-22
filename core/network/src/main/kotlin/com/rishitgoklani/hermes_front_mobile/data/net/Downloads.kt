package com.rishitgoklani.hermes_front_mobile.data.net

import com.rishitgoklani.hermes_front_mobile.domain.system.UrlGuard
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

object Downloads {
    data class Result(val bytes: Long, val resumed: Boolean, val sha256: String)

    /** Downloads [url] to [dest], resuming a partial file with HTTP Range when the server supports it; verifies [expectedSha256] if given. */
    fun fetch(client: OkHttpClient, url: String, dest: File, expectedSha256: String? = null): Result {
        UrlGuard.check(url)?.let { throw IOException(it) }
        dest.parentFile?.mkdirs()
        val have = if (dest.exists()) dest.length() else 0L
        val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        var resumed = false
        client.newCall(req).execute().use { r ->
            if (r.code == 416) { /* already complete */ resumed = true }
            else {
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                resumed = have > 0 && r.code == 206
                RandomAccessFile(dest, "rw").use { f ->
                    if (resumed) f.seek(have) else f.setLength(0)
                    r.body!!.byteStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; f.write(buf, 0, n) } }
                }
            }
        }
        val sha = sha256(dest)
        if (expectedSha256 != null && !sha.equals(expectedSha256, true)) { dest.delete(); throw IOException("checksum mismatch ($sha)") }
        return Result(dest.length(), resumed, sha)
    }

    fun sha256(f: File): String = hash(f, "SHA-256")
    fun hash(f: File, algo: String): String { val md = MessageDigest.getInstance(algo); f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }; return md.digest().joinToString("") { "%02x".format(it) } }
}
