package com.musicfree.player.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ImportProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val current: String = "",
    val message: String? = null
)

private class DriveException(message: String) : Exception(message)

/**
 * Baixa os áudios de uma pasta pública do Google Drive (link "qualquer pessoa com o link")
 * para Music/MusicFree no armazenamento do aparelho, via MediaStore (sem permissão extra).
 */
class DriveImporter(private val context: Context) {

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    private class DriveFile(val id: String, val name: String, val mime: String, val path: String)

    private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ape", "alac", "oga", "mka", "amr")
    private val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Aceita o link da pasta ou só o ID. */
    fun parseFolderId(input: String): String? {
        val t = input.trim()
        Regex("""/folders/([A-Za-z0-9_-]{10,})""").find(t)?.let { return it.groupValues[1] }
        Regex("""[?&]id=([A-Za-z0-9_-]{10,})""").find(t)?.let { return it.groupValues[1] }
        return t.takeIf { Regex("""[A-Za-z0-9_-]{15,}""").matches(it) }
    }

    suspend fun import(folderId: String, apiKey: String) = withContext(Dispatchers.IO) {
        _progress.value = ImportProgress(running = true, current = "Listando arquivos…")
        try {
            val files = listAudio(folderId, apiKey)
            if (files.isEmpty()) {
                _progress.value = ImportProgress(message = "Nenhum arquivo de áudio encontrado nessa pasta.")
                return@withContext
            }
            var done = 0
            var skipped = 0
            var failed = 0
            var lastError: String? = null
            for (f in files) {
                currentCoroutineContext().ensureActive()
                _progress.value = ImportProgress(true, done, files.size, skipped, failed, f.name)
                val rel = relativePath(f.path)
                if (exists(f.name, rel)) {
                    skipped++
                } else {
                    try {
                        download(f, rel, apiKey)
                    } catch (e: DriveException) {
                        failed++
                        lastError = e.message
                    } catch (e: java.io.IOException) {
                        failed++
                        lastError = "falha de rede"
                    }
                }
                done++
            }
            val baixadas = done - skipped - failed
            _progress.value = ImportProgress(
                total = files.size, done = done, skipped = skipped, failed = failed,
                message = "Concluído: $baixadas baixadas, $skipped já existiam, $failed com erro" +
                    (lastError?.let { " ($it)" } ?: "")
            )
        } catch (e: DriveException) {
            _progress.value = ImportProgress(message = e.message)
        } catch (e: java.io.IOException) {
            _progress.value = ImportProgress(message = "Sem conexão com a internet.")
        } finally {
            if (_progress.value.running) _progress.value = _progress.value.copy(running = false, current = "")
        }
    }

    private fun relativePath(path: String): String {
        val sub = path.split('/').filter { it.isNotBlank() }.joinToString("/") { sanitize(it) }
        return if (sub.isEmpty()) "Music/MusicFree/" else "Music/MusicFree/$sub/"
    }

    private fun sanitize(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "_" }

    private suspend fun listAudio(rootId: String, key: String): List<DriveFile> {
        val result = mutableListOf<DriveFile>()
        val pending = ArrayDeque(listOf(rootId to ""))
        var folders = 0
        while (pending.isNotEmpty() && folders < 500) {
            currentCoroutineContext().ensureActive()
            val (folderId, path) = pending.removeFirst()
            folders++
            var pageToken: String? = null
            do {
                val q = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
                val fields = URLEncoder.encode("nextPageToken,files(id,name,mimeType)", "UTF-8")
                var url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=$fields&pageSize=1000" +
                    "&supportsAllDrives=true&includeItemsFromAllDrives=true&key=${URLEncoder.encode(key, "UTF-8")}"
                if (pageToken != null) url += "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
                val resp = Http.get(url) ?: throw java.io.IOException()
                if (!resp.ok) throw DriveException(explain(resp.code, resp.text))
                val json = JSONObject(resp.text)
                val arr = json.optJSONArray("files")
                for (i in 0 until (arr?.length() ?: 0)) {
                    val o = arr!!.getJSONObject(i)
                    val name = o.getString("name")
                    val mime = o.optString("mimeType")
                    when {
                        mime == "application/vnd.google-apps.folder" ->
                            pending.addLast(o.getString("id") to (if (path.isEmpty()) name else "$path/$name"))
                        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExt ->
                            result += DriveFile(o.getString("id"), name, mime, path)
                    }
                }
                pageToken = json.optString("nextPageToken").ifEmpty { null }
            } while (pageToken != null)
        }
        return result
    }

    private fun explain(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        return when (code) {
            400 -> "Chave de API inválida ou link incorreto."
            403 -> "Acesso negado. A pasta precisa estar compartilhada como \"qualquer pessoa com o link\" e a Drive API ativada para a chave. ${msg.orEmpty()}".trim()
            404 -> "Pasta não encontrada. Confira o link e o compartilhamento."
            else -> "Erro do Drive ($code). ${msg.orEmpty()}".trim()
        }
    }

    private fun exists(name: String, rel: String): Boolean {
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(sanitize(name), rel),
            null
        )?.use { it.count > 0 } ?: false
    }

    private suspend fun download(f: DriveFile, rel: String, key: String) {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val mime = (MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: f.mime)
            .takeIf { it.startsWith("audio/") } ?: "audio/${ext.ifEmpty { "mpeg" }}"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, sanitize(f.name))
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, rel)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw DriveException("não foi possível criar o arquivo")
        try {
            val url = "https://www.googleapis.com/drive/v3/files/${f.id}?alt=media&supportsAllDrives=true" +
                "&key=${URLEncoder.encode(key, "UTF-8")}"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            if (c.responseCode !in 200..299) {
                val body = c.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                throw DriveException(explain(c.responseCode, body))
            }
            resolver.openOutputStream(uri)!!.use { out ->
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }
}
