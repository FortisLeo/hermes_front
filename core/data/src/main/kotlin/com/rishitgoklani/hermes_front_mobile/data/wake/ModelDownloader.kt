package com.rishitgoklani.hermes_front_mobile.data.wake

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ModelDownloader {
    const val SMALL_EN_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    /** Pinned: sha256 of vosk-model-small-en-us-0.15.zip (Apache-2.0) as downloaded 2026-11-22. */
    const val SMALL_EN_SHA256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"

    private val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun download(url: String, to: File) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("model download HTTP ${r.code}")
            r.body!!.byteStream().use { input -> to.outputStream().use { input.copyTo(it) } }
        }
    }
}
