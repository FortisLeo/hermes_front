package com.rishitgoklani.hermes_front_mobile

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.rishitgoklani.hermes_front_mobile.domain.QualityPlanner
import com.rishitgoklani.hermes_front_mobile.domain.behavior.HandleShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/** Invisible target for the system share sheet: forwards shared text/images to Hermes and finishes immediately. */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DeskbotApp).container
        val text = intent.getStringExtra(Intent.EXTRA_TEXT); val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val image = uri?.takeIf { intent.type?.startsWith("image/") == true }?.let { runCatching { shrink(it) }.getOrNull() }
        Toast.makeText(this, "Sent to Deskbot", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch { runCatching { HandleShare(container.hermes, container.speaker)(text, image, subject) } }
        finish()
    }

    private fun shrink(uri: Uri): String {
        val bmp = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val scale = QualityPlanner.scaleFor(bmp.width, bmp.height, 1024)
        val b = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream().also { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
