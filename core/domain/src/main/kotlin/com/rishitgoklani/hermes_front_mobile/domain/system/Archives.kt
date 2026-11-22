package com.rishitgoklani.hermes_front_mobile.domain.system

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Archives {
    /** Zips a file or folder into [dest]. Returns the number of files added. */
    fun zip(source: File, dest: File): Int {
        var n = 0; dest.parentFile?.mkdirs()
        ZipOutputStream(dest.outputStream().buffered()).use { z ->
            val base = if (source.isDirectory) source else source.parentFile
            source.walkTopDown().filter { it.isFile && it != dest }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(base).path.replace(File.separatorChar, '/'))); f.inputStream().use { it.copyTo(z) }; z.closeEntry(); n++
            }
        }
        return n
    }

    /** Extracts into [dest]; rejects entries escaping it (zip-slip) and caps total size to avoid zip bombs. */
    fun unzip(zip: File, dest: File, maxBytes: Long = 2L * 1024 * 1024 * 1024): Int {
        dest.mkdirs(); val root = dest.canonicalFile; var count = 0; var total = 0L
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val target = File(root, e.name).canonicalFile
                if (target != root && !target.path.startsWith(root.path + File.separator)) throw SecurityException("entry escapes target: ${e.name}")
                if (e.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> val buf = ByteArray(64 * 1024); while (true) { val r = zin.read(buf); if (r < 0) break; total += r; if (total > maxBytes) throw SecurityException("archive too large"); out.write(buf, 0, r) } }
                count++
            }
        }
        return count
    }
}

/** One 0-100 health number from the signals the bot already has; also names the worst offender. */
object HealthScore {
    data class Input(val batteryPct: Int, val charging: Boolean, val tempC: Double, val storageFreePct: Int, val online: Boolean, val recentFailures: Int, val killActive: Boolean = false)
    data class Result(val score: Int, val issue: String)

    fun compute(i: Input): Result {
        val penalties = linkedMapOf<String, Int>()
        if (i.batteryPct in 0..19 && !i.charging) penalties["battery low (${i.batteryPct}%)"] = 25 else if (i.batteryPct in 20..39 && !i.charging) penalties["battery getting low"] = 8
        if (i.tempC >= 45) penalties["battery hot (${i.tempC.toInt()} °C)"] = 30 else if (i.tempC >= 40) penalties["battery warm"] = 10
        if (i.storageFreePct < 5) penalties["storage almost full"] = 25 else if (i.storageFreePct < 15) penalties["storage low"] = 10
        if (!i.online) penalties["offline"] = 25
        if (i.recentFailures >= 10) penalties["many tool failures (${i.recentFailures})"] = 15 else if (i.recentFailures >= 3) penalties["some tool failures"] = 5
        if (i.killActive) penalties["kill switch active"] = 40
        val score = (100 - penalties.values.sum()).coerceIn(0, 100)
        return Result(score, penalties.maxByOrNull { it.value }?.key ?: "none")
    }
}
