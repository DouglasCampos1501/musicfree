package com.musicfree.player.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

data class LrcLine(val timeMs: Long, val text: String)

/** Letra de uma música: sincronizada ([synced] = true) ou texto simples. */
data class Lyrics(val lines: List<LrcLine>, val synced: Boolean)

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): Lyrics {
        val synced = mutableListOf<LrcLine>()
        val plain = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) {
                val t = line.trim()
                // Linhas de metadados como [ar:Artista] ou [ti:Título] são ignoradas.
                if (t.isNotEmpty() && !Regex("""^\[[a-zA-Z]+:.*]$""").matches(t)) plain += t
                return@forEach
            }
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracStr = m.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.take(3).toLong()
                }
                synced += LrcLine((min * 60 + sec) * 1000 + frac, text)
            }
        }
        return if (synced.isNotEmpty()) {
            Lyrics(synced.sortedBy { it.timeMs }, synced = true)
        } else {
            Lyrics(plain.map { LrcLine(0, it) }, synced = false)
        }
    }

    /** Índice da linha ativa para a posição [positionMs], ou -1 antes da primeira. */
    fun activeIndex(lines: List<LrcLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/**
 * Letras ficam em arquivos `.lrc` copiados para o armazenamento interno do app (um por música)
 * ou são achadas automaticamente numa pasta de letras escolhida pelo usuário, casando
 * `nome-do-arquivo-da-música.lrc`.
 */
class LyricsRepository(private val context: Context, private val prefs: Prefs) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private var folderIndex: Map<String, Uri>? = null

    private fun fileFor(songId: Long) = File(dir, "$songId.lrc")

    fun hasLocal(songId: Long) = fileFor(songId).exists()

    suspend fun load(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val local = fileFor(song.id)
        val raw = when {
            local.exists() -> local.readText()
            else -> findInFolder(song)?.let { readUri(it) }
        }
        raw?.takeIf { it.isNotBlank() }?.let { LrcParser.parse(it) }
    }

    /** Copia o arquivo escolhido pelo usuário como letra da [song]. */
    suspend fun import(song: Song, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = readUri(uri) ?: return@withContext false
        fileFor(song.id).writeText(text)
        true
    }

    suspend fun remove(song: Song) = withContext(Dispatchers.IO) { fileFor(song.id).delete() }

    /** Reindexa a pasta de letras (chamar ao escolher ou trocar a pasta). */
    suspend fun refreshFolderIndex() = withContext(Dispatchers.IO) {
        folderIndex = null
        val tree = prefs.lyricsTree.first()?.let { Uri.parse(it) } ?: return@withContext
        folderIndex = runCatching { indexTree(tree) }.getOrDefault(emptyMap())
    }

    private suspend fun findInFolder(song: Song): Uri? {
        if (folderIndex == null) refreshFolderIndex()
        val index = folderIndex ?: return null
        val base = song.fileName.substringBeforeLast('.').lowercase()
        return index["$base.lrc"] ?: index["${song.title.lowercase()}.lrc"]
    }

    private fun indexTree(tree: Uri): Map<String, Uri> {
        val result = mutableMapOf<String, Uri>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val pending = ArrayDeque(listOf(rootId))
        while (pending.isNotEmpty() && result.size < 20_000) {
            val parentId = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.addLast(id)
                    } else if (name.endsWith(".lrc", ignoreCase = true)) {
                        result[name.lowercase()] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            }
        }
        return result
    }

    private fun readUri(uri: Uri): String? =
        runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
}
