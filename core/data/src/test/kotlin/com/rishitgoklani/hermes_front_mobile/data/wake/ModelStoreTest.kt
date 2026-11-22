package com.rishitgoklani.hermes_front_mobile.data.wake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModelStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>): File = File(tmp.root, "src${entries.hashCode()}.zip").also { f ->
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
    }
    private fun goodZip() = zip("vosk-model-small-en-us-0.15/conf/model.conf" to "x", "vosk-model-small-en-us-0.15/README" to "r")

    @Test fun sha256Known() { val f = tmp.newFile(); f.writeText("abc"); assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ModelFiles.sha256(f)) }
    @Test fun unzipExtractsFiles() { val dest = File(tmp.root, "d"); assertEquals(2, ModelFiles.unzipSafely(goodZip(), dest)); assertTrue(File(dest, "vosk-model-small-en-us-0.15/conf/model.conf").exists()) }
    @Test fun zipSlipRejected() { assertThrows(SecurityException::class.java) { ModelFiles.unzipSafely(zip("../evil.txt" to "x"), File(tmp.root, "d2")) }; assertFalse(File(tmp.root, "evil.txt").exists()) }
    @Test fun ensureDownloadsVerifiesAndInstalls() = runBlocking {
        val src = goodZip(); val store = ModelStore(File(tmp.root, "base"), ModelFiles.sha256(src), { to -> src.copyTo(to, overwrite = true) })
        assertFalse(store.isReady()); store.ensure(); assertTrue(store.isReady())
    }
    @Test fun ensureIsIdempotentAndSkipsDownload() = runBlocking {
        val src = goodZip(); var downloads = 0; val store = ModelStore(File(tmp.root, "base"), ModelFiles.sha256(src), { to -> downloads++; src.copyTo(to, overwrite = true) })
        store.ensure(); store.ensure(); assertEquals(1, downloads)
    }
    @Test fun checksumMismatchRejectedAndNothingInstalled() {
        val src = goodZip(); val store = ModelStore(File(tmp.root, "base"), "0".repeat(64), { to -> src.copyTo(to, overwrite = true) })
        assertThrows(java.io.IOException::class.java) { runBlocking { store.ensure() } }; assertFalse(store.isReady()); assertFalse(File(tmp.root, "base/model.zip.part").exists())
    }
    @Test fun incompleteModelRejected() {
        val src = zip("vosk-model-small-en-us-0.15/README" to "r"); val store = ModelStore(File(tmp.root, "base"), ModelFiles.sha256(src), { to -> src.copyTo(to, overwrite = true) })
        assertThrows(IllegalStateException::class.java) { runBlocking { store.ensure() } }
    }
}
