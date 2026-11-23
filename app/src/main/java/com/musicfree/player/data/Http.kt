package com.musicfree.player.data

import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP mínimo (sem dependências extras) para as APIs de Drive, MusicBrainz e LRCLIB. */
object Http {
    const val USER_AGENT = "MusicFree/1.0 (Android music player)"

    class Response(val code: Int, val body: ByteArray) {
        val ok get() = code in 200..299
        val text get() = body.toString(Charsets.UTF_8)
    }

    fun get(url: String): Response? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", USER_AGENT)
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        Response(code, stream?.use { it.readBytes() } ?: ByteArray(0))
    } catch (_: java.io.IOException) {
        null
    }
}
