package com.musicfree.player.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicfree.player.MainViewModel
import com.musicfree.player.data.ThemeMode
import com.musicfree.player.playback.EqInfo
import com.musicfree.player.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val eqEnabledSaved by viewModel.eqEnabled.collectAsState()
    val eqLevelsSaved by viewModel.eqLevels.collectAsState()
    val loudnessSaved by viewModel.loudnessMb.collectAsState()

    var eqInfo by remember { mutableStateOf<EqInfo?>(null) }
    var eqUnavailable by remember { mutableStateOf(false) }

    // O serviço pode demorar um instante para ficar pronto; tenta algumas vezes.
    LaunchedEffect(Unit) {
        repeat(5) {
            val info = viewModel.player.getEqInfo()
            if (info != null) {
                eqInfo = info
                return@LaunchedEffect
            }
            delay(600)
        }
        eqUnavailable = true
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle("Aparência")
        ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == themeMode, onClick = { viewModel.setThemeMode(mode) })
                Text(mode.label)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cores do sistema (Material You)")
                    Text(
                        "Usa as cores do seu papel de parede",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = dynamicColor, onCheckedChange = { viewModel.setDynamicColor(it) })
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Equalizador")

        val info = eqInfo
        if (info == null) {
            Text(
                if (eqUnavailable) "Equalizador indisponível neste aparelho." else "Carregando…",
                color = TextSecondary
            )
        } else {
            // Níveis locais para o slider responder na hora; só persiste ao soltar.
            var levels by remember(info, eqLevelsSaved) {
                mutableStateOf(List(info.bands) { eqLevelsSaved.getOrElse(it) { 0 } })
            }
            var enabled by remember(eqEnabledSaved) { mutableStateOf(eqEnabledSaved) }
            var loudness by remember(loudnessSaved) { mutableStateOf(loudnessSaved.toFloat()) }

            fun commit() = viewModel.player.applyEq(enabled, levels, loudness.toInt())

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ativar equalizador")
                Switch(checked = enabled, onCheckedChange = { enabled = it; commit() })
            }

            for (band in 0 until info.bands) {
                val hz = info.freqsHz.getOrNull(band) ?: 0
                val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.padding(end = 8.dp).weight(0.25f), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = levels[band].toFloat(),
                        onValueChange = { v -> levels = levels.toMutableList().also { it[band] = v.toInt() } },
                        onValueChangeFinished = { commit() },
                        valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                        enabled = enabled,
                        modifier = Modifier.weight(0.75f)
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    levels = List(info.bands) { 0 }
                    commit()
                },
                enabled = enabled
            ) { Text("Zerar bandas") }

            Text("Reforço de volume: +${(loudness / 10).toInt() / 10f} dB", modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = loudness,
                onValueChange = { loudness = it },
                onValueChangeFinished = { commit() },
                valueRange = 0f..1500f
            )
            Text(
                "O reforço aumenta o volume de faixas gravadas baixo. Valores altos podem distorcer.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Letras")
        val lyricsFolder by viewModel.lyricsFolder.collectAsState()
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) viewModel.setLyricsFolder(uri)
        }
        Text(
            if (lyricsFolder != null) "Pasta de letras definida. Arquivos .lrc com o mesmo nome da música são usados automaticamente."
            else "Escolha uma pasta com arquivos .lrc (mesmo nome da música) para as letras aparecerem sozinhas.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Escolher pasta") }
            if (lyricsFolder != null) {
                OutlinedButton(onClick = { viewModel.setLyricsFolder(null) }) { Text("Remover pasta") }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Importar do Google Drive")
        val savedKey by viewModel.driveApiKey.collectAsState()
        val drive by viewModel.driveProgress.collectAsState()
        var keyText by remember(savedKey) { mutableStateOf(savedKey) }
        var linkText by remember { mutableStateOf("") }
        Text(
            "Coloque as músicas numa pasta do Drive compartilhada como \"qualquer pessoa com o link\", " +
                "cole o link abaixo e o app baixa tudo para Música/MusicFree. Mantenha o app aberto durante o download. " +
                "Precisa de uma chave de API gratuita (Google Cloud → ativar \"Google Drive API\" → Credenciais → Chave de API).",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            label = { Text("Chave de API do Google") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = linkText,
            onValueChange = { linkText = it },
            label = { Text("Link da pasta do Drive") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !drive.running,
                onClick = {
                    viewModel.setDriveApiKey(keyText)
                    viewModel.importFromDrive(linkText)
                }
            ) { Text("Baixar músicas") }
            if (drive.running) {
                OutlinedButton(onClick = { viewModel.cancelDriveImport() }) { Text("Cancelar") }
            }
        }
        if (drive.running) {
            LinearProgressIndicator(
                progress = { if (drive.total > 0) drive.done / drive.total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (drive.total > 0) "${drive.done}/${drive.total} — ${drive.current}" else drive.current,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        drive.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Corrigir nomes, capas e letras")
        val fix by viewModel.fixProgress.collectAsState()
        Text(
            "Procura na internet (MusicBrainz, Cover Art Archive e LRCLIB) o nome, artista, álbum, capa e letra " +
                "usando as tags, o nome do arquivo e a duração. Nomes como \"Faixa 1\" sem nenhuma outra pista " +
                "não podem ser identificados. As correções ficam só no app; os arquivos não são alterados. " +
                "Leva cerca de 1 segundo por música.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !fix.running, onClick = { viewModel.fixMetadata(true) }) {
                Text("Só incompletas")
            }
            OutlinedButton(enabled = !fix.running, onClick = { viewModel.fixMetadata(false) }) {
                Text("Biblioteca toda")
            }
            if (fix.running) {
                OutlinedButton(onClick = { viewModel.cancelFix() }) { Text("Parar") }
            }
        }
        if (fix.running) {
            LinearProgressIndicator(
                progress = { if (fix.total > 0) fix.done / fix.total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${fix.done}/${fix.total} — ${fix.current}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        fix.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(enabled = !fix.running, onClick = { viewModel.undoFixes() }) { Text("Desfazer correções automáticas") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Biblioteca")
        OutlinedButton(onClick = { viewModel.refreshLibrary() }) { Text("Atualizar biblioteca") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
