package com.musicfree.player.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.LrcParser
import com.musicfree.player.data.Lyrics
import com.musicfree.player.data.Song
import com.musicfree.player.ui.theme.SpotifyGreen
import com.musicfree.player.ui.theme.TextSecondary

/** Letra da música atual: sincronizada (linha ativa em destaque, rolagem automática) ou texto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(viewModel: MainViewModel, song: Song, onDismiss: () -> Unit) {
    val state by viewModel.playerState.collectAsState()
    val version by viewModel.lyricsVersion.collectAsState()
    val lyrics by produceState<Lyrics?>(initialValue = null, song.id, version) {
        value = viewModel.lyricsRepository.load(song)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLyrics(song, uri)
    }
    val listState = rememberLazyListState()
    val current = lyrics
    val activeIndex = if (current != null && current.synced) {
        LrcParser.activeIndex(current.lines, state.positionMs)
    } else -1

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importar .lrc") }
        }
        if (current == null || current.lines.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "Nenhuma letra para esta música.\nImporte um arquivo .lrc ou escolha uma pasta de letras em Ajustes " +
                        "(o arquivo deve ter o mesmo nome da música).",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(top = 12.dp)
                ) { Text("Escolher arquivo de letra") }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 520.dp).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(current.lines) { index, line ->
                    val active = current.synced && index == activeIndex
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            !current.synced -> MaterialTheme.colorScheme.onSurface
                            active -> SpotifyGreen
                            else -> TextSecondary
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            TextButton(
                onClick = { viewModel.removeLyrics(song) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("Remover letra importada") }
        }
    }
}
