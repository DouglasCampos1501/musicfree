package com.musicfree.player.data

import android.content.Context
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs

const val UNKNOWN_ARTIST = "Artista desconhecido"
const val UNKNOWN_ALBUM = "Álbum desconhecido"

/** Limpeza de nomes de arquivo ("03 - Artista_Música (Official Video)") e detecção de nomes genéricos. */
object NameCleaner {
    private val generic = Regex(
        """^((faixa|track|audio|audiotrack|pista|musica|música|unknown|desconhecido|untitled|sem t[ií]tulo|recording|gravação|gravacao|voice)[\s_.-]*\d{0,4}|\d{1,3})$""",
        RegexOption.IGNORE_CASE
    )
    private val junkBrackets = Regex(
        """\s*[(\[][^)\]]*(official|video|vídeo|lyrics?|letra|audio|áudio|\bhd\b|\bhq\b|clipe|oficial|remaster|download|baixar)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )

    fun isGeneric(s: String?) = s.isNullOrBlank() || generic.matches(s.trim())

    fun clean(raw: String): String {
        var s = raw.replace('_', ' ')
        s = s.replace(Regex("""^\s*\d{1,3}\s*[-.)]\s*"""), "")
        s = s.replace(Regex("""^\s*\d{1,2}\s+(?=\D)"""), "")
        s = s.replace(junkBrackets, "")
        s = s.replace(Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE), "")
        return s.replace(Regex("""\s+"""), " ").trim()
    }
}

data class FixProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val renamed: Int = 0,
    val covers: Int = 0,
    val lyrics: Int = 0,
    val message: String? = null
)

/**
 * Descobre título/artista/álbum, capa e letra das músicas pela internet:
 * MusicBrainz (identificação), Cover Art Archive (capas) e LRCLIB (letras, inclusive sincronizadas).
 * A identificação usa as tags, o nome do arquivo e a duração. Tudo é salvo só no banco do app.
 */
class MetadataFixer(
    private val context: Context,
    private val repo: MusicRepository,
    private val lyrics: LyricsRepository
) {
    private val _progress = MutableStateFlow(FixProgress())
    val progress: StateFlow<FixProgress> = _progress.asStateFlow()

    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }

    private class Match(val title: String, val artist: String, val album: String?, val releaseIds: List<String>)
    private class Query(val title: String, val artist: String?)

    private fun isIncomplete(s: Song) =
        NameCleaner.isGeneric(s.title) || s.artist == UNKNOWN_ARTIST || s.album == UNKNOWN_ALBUM

    suspend fun run(onlyIncomplete: Boolean) = withContext(Dispatchers.IO) {
        val processed = repo.processedMetaIds().toSet()
        val candidates = repo.visibleSongs.first()
            .filter { it.id !in processed && (!onlyIncomplete || isIncomplete(it)) }
        if (candidates.isEmpty()) {
            _progress.value = FixProgress(message = "Nada novo para corrigir.")
            return@withContext
        }
        var renamed = 0
        var covers = 0
        var lyr = 0
        var offline = false
        _progress.value = FixProgress(running = true, total = candidates.size)
        try {
            for ((i, song) in candidates.withIndex()) {
                currentCoroutineContext().ensureActive()
                _progress.value = FixProgress(true, i, candidates.size, song.title, renamed, covers, lyr)
                val r = try {
                    processOne(song)
                } catch (e: java.io.IOException) {
                    offline = true
                    break
                }
                if (r.renamed) renamed++
                if (r.cover) covers++
                if (r.lyrics) lyr++
            }
            _progress.value = FixProgress(
                done = candidates.size, total = candidates.size, renamed = renamed, covers = covers, lyrics = lyr,
                message = if (offline) "Sem conexão. O que já foi feito foi mantido; rode de novo para continuar."
                else "Concluído: $renamed nomes corrigidos, $covers capas e $lyr letras."
            )
        } finally {
            if (_progress.value.running) _progress.value = _progress.value.copy(running = false, current = "")
        }
    }

    private class Result(val renamed: Boolean, val cover: Boolean, val lyrics: Boolean)

    private suspend fun processOne(s: Song): Result {
        val q = buildQuery(s)
        var newTitle: String? = null
        var newArtist: String? = null
        var newAlbum: String? = null
        var coverPath: String? = null
        var searchTitle = q?.title
        var searchArtist = q?.artist

        if (q != null) {
            val m = searchMusicBrainz(q, s.durationMs)
            if (m != null) {
                if (NameCleaner.isGeneric(s.title)) newTitle = m.title
                if (s.artist == UNKNOWN_ARTIST) newArtist = m.artist
                if (s.album == UNKNOWN_ALBUM) newAlbum = m.album
                searchTitle = m.title
                searchArtist = m.artist
                if (!hasArt(s)) coverPath = fetchCover(s.id, m.releaseIds)
            } else {
                // Sem correspondência online: pelo menos usa o nome do arquivo já limpo.
                if (NameCleaner.isGeneric(s.title)) newTitle = q.title
                if (s.artist == UNKNOWN_ARTIST && q.artist != null) newArtist = q.artist
            }
        }

        var gotLyrics = false
        if (searchTitle != null && !lyrics.hasLocal(s.id)) {
            fetchLyrics(searchTitle, searchArtist, s.durationMs)?.let {
                lyrics.save(s, it)
                gotLyrics = true
            }
        }

        repo.saveMeta(SongMetaEntity(s.id, newTitle, newArtist, newAlbum, coverPath))
        return Result(newTitle != null || newArtist != null || newAlbum != null, coverPath != null, gotLyrics)
    }

    private fun buildQuery(s: Song): Query? {
        val stem = s.fileName.substringBeforeLast('.')
        var title = NameCleaner.clean(if (NameCleaner.isGeneric(s.title)) stem else s.title)
        var artist = s.artist.takeUnless { it == UNKNOWN_ARTIST }
        if (artist == null && " - " in title) {
            artist = title.substringBefore(" - ").trim().ifEmpty { null }
            title = title.substringAfter(" - ").trim()
        }
        if (NameCleaner.isGeneric(title)) return null
        return Query(title, artist)
    }

    private fun lucene(s: String) = s.replace("\"", " ").replace("\\", " ")

    private suspend fun searchMusicBrainz(q: Query, durationMs: Long): Match? {
        var query = "recording:\"${lucene(q.title)}\""
        if (q.artist != null) query += " AND artist:\"${lucene(q.artist)}\""
        val url = "https://musicbrainz.org/ws/2/recording/?fmt=json&limit=8&query=${URLEncoder.encode(query, "UTF-8")}"
        val resp = Http.get(url)
        delay(1100) // MusicBrainz pede no máximo ~1 requisição por segundo.
        if (resp == null) throw java.io.IOException()
        if (!resp.ok) return null
        val recs = JSONObject(resp.text).optJSONArray("recordings") ?: return null
        for (i in 0 until recs.length()) {
            val r = recs.getJSONObject(i)
            if (r.optInt("score") < 90) continue
            val length = r.optLong("length", 0L)
            if (durationMs > 0 && length > 0 && abs(length - durationMs) > 7000) continue
            val credit = r.optJSONArray("artist-credit")
            val artist = buildString {
                for (k in 0 until (credit?.length() ?: 0)) {
                    val c = credit!!.getJSONObject(k)
                    append(c.optString("name"))
                    append(c.optString("joinphrase"))
                }
            }.trim()
            if (artist.isEmpty()) continue
            val releases = r.optJSONArray("releases") ?: JSONArray()
            val ordered = (0 until releases.length()).map { releases.getJSONObject(it) }
                .sortedBy { rel ->
                    val official = rel.optString("status") == "Official"
                    val album = rel.optJSONObject("release-group")?.optString("primary-type") == "Album"
                    (if (official) 0 else 2) + (if (album) 0 else 1)
                }
            return Match(
                title = r.optString("title").ifEmpty { q.title },
                artist = artist,
                album = ordered.firstOrNull()?.optString("title")?.ifEmpty { null },
                releaseIds = ordered.take(3).map { it.getString("id") }
            )
        }
        return null
    }

    private fun hasArt(s: Song): Boolean =
        runCatching { context.contentResolver.loadThumbnail(s.contentUri, Size(96, 96), null).recycle(); true }
            .getOrDefault(false)

    private fun fetchCover(songId: Long, releaseIds: List<String>): String? {
        for (rid in releaseIds) {
            val resp = Http.get("https://coverartarchive.org/release/$rid/front-500") ?: continue
            if (resp.ok && resp.body.size > 1000) {
                val file = File(coverDir, "${songId}_${System.currentTimeMillis()}.jpg")
                file.writeBytes(resp.body)
                return file.absolutePath
            }
        }
        return null
    }

    private fun fetchLyrics(title: String, artist: String?, durationMs: Long): String? {
        val params = if (artist != null) {
            "track_name=${URLEncoder.encode(title, "UTF-8")}&artist_name=${URLEncoder.encode(artist, "UTF-8")}"
        } else {
            "q=${URLEncoder.encode(title, "UTF-8")}"
        }
        val resp = Http.get("https://lrclib.net/api/search?$params") ?: throw java.io.IOException()
        if (!resp.ok) return null
        val arr = JSONArray(resp.text)
        var plain: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dur = o.optDouble("duration", 0.0) * 1000
            if (durationMs > 0 && dur > 0 && abs(dur - durationMs) > 6000) continue
            val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (synced != null) return synced
            if (plain == null) plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        }
        return plain
    }
}
