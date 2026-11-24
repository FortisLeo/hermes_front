package com.rishitgoklani.hermes_front_mobile.data.vision

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisionDataTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun pixels(m: BitMatrix, margin: Int = 20, scale: Int = 6, invert: Boolean = false): Triple<Int, Int, IntArray> {
        val w = m.width * scale + 2 * margin; val h = m.height * scale + 2 * margin
        val px = IntArray(w * h) { if (invert) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) for (dy in 0 until scale) for (dx in 0 until scale)
            px[(margin + y * scale + dy) * w + margin + x * scale + dx] = if (invert) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        return Triple(w, h, px)
    }
    private fun matrix(text: String, f: BarcodeFormat, w: Int = 0, h: Int = 0) = MultiFormatWriter().encode(text, f, w, h)

    @Test fun decodesQrCode() {
        val (w, h, px) = pixels(matrix("https://example.com/deskbot", BarcodeFormat.QR_CODE, 0, 0).let { it })
        val r = ZxingDecoder.decode(w, h, px).single()
        assertEquals("QR_CODE", r.format); assertEquals("https://example.com/deskbot", r.text); assertEquals("url", r.kind)
    }
    @Test fun decodesEan13Barcode() {
        val (w, h, px) = pixels(matrix("4006381333931", BarcodeFormat.EAN_13, 200, 80), scale = 3)
        val r = ZxingDecoder.decode(w, h, px).single(); assertEquals("EAN_13", r.format); assertEquals("4006381333931", r.text)
    }
    @Test fun decodesInvertedQr() {
        val (w, h, px) = pixels(matrix("WIFI:S:desk;T:WPA;P:secret;;", BarcodeFormat.QR_CODE), invert = true)
        assertEquals("wifi", ZxingDecoder.decode(w, h, px).single().kind)
    }
    @Test fun blankImageHasNoCodes() = assertTrue(ZxingDecoder.decode(50, 50, IntArray(2500) { 0xFFFFFFFF.toInt() }).isEmpty())

    @Test fun scannerDecodesJpegFrame() = runBlocking {
        val (w, h, px) = pixels(matrix("hello-front-camera", BarcodeFormat.QR_CODE))
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        assertEquals("hello-front-camera", ZxingCodeScanner().scan(jpeg).single().text)
    }
    @Test fun scannerToleratesGarbageBytes() = runBlocking { assertTrue(ZxingCodeScanner().scan(byteArrayOf(1, 2, 3)).isEmpty()) }

    @Test fun thumbnailerGivesComparableGrayscale() {
        fun jpeg(color: Int) = ByteArrayOutputStream().also { Bitmap.createBitmap(IntArray(64 * 64) { color }, 64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val t = com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidFrameThumbnailer()
        val dark = t.thumb(jpeg(0xFF202020.toInt())); val light = t.thumb(jpeg(0xFFE0E0E0.toInt()))
        assertEquals(1024, dark.size)
        assertTrue(com.rishitgoklani.hermes_front_mobile.domain.sense.FrameDiff.score(dark, light) > 100)
        assertEquals(0.0, com.rishitgoklani.hermes_front_mobile.domain.sense.FrameDiff.score(dark, dark), 0.0)
    }

    @Test fun frameSinkWritesIntoSessionFolder() = runBlocking {
        val root = tmp.newFolder("tl")
        val path = FileFrameSink(root).save("lapse1", "f_0000.jpg", byteArrayOf(7, 8))
        assertEquals(File(root, "lapse1/f_0000.jpg").path, path); assertEquals(listOf<Byte>(7, 8), File(path).readBytes().toList())
    }
    @Test fun frameSinkCannotEscapeRoot() = runBlocking {
        val root = tmp.newFolder("tl2")
        val path = FileFrameSink(root).save("../../evil", "../x.jpg", byteArrayOf(1))
        assertTrue(File(path).canonicalPath.startsWith(root.canonicalPath))
    }
    @Test fun frameSinkFallsBackWhenRootUnwritable() = runBlocking {
        val blocker = tmp.newFile("notadir"); val fb = tmp.newFolder("fb")
        val path = FileFrameSink(File(blocker, "sub"), fb).save("s", "a.jpg", byteArrayOf(1))
        assertTrue(path.startsWith(fb.path))
    }
}
