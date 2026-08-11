package com.musicfree.player

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.musicfree.player.ui.navigation.AppNavGraph
import com.musicfree.player.ui.theme.MusicFreeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requiredPermissions: Array<String> =
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)

    private val hasPermissionState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPermissionState.value = hasAllPermissions()

        setContent {
            var hasPermission by hasPermissionState
            var requestedOnce by remember { mutableStateOf(false) }
            var permanentlyDenied by remember { mutableStateOf(false) }

            val permissionLauncher = rememberPermissionLauncher { granted ->
                hasPermission = granted
                if (granted) {
                    viewModel.refreshLibrary()
                } else {
                    // Se o sistema não vai mais mostrar o diálogo de permissão, o usuário
                    // marcou "não perguntar novamente" — só resta abrir as Configurações.
                    permanentlyDenied = requestedOnce && !shouldShowRequestPermissionRationale(
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                    requestedOnce = true
                }
            }

            MusicFreeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (hasPermission) {
                        AppNavGraph(viewModel = viewModel)
                    } else {
                        PermissionRequestScreen(
                            permanentlyDenied = permanentlyDenied,
                            onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
                            onOpenSettings = { openAppSettings() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Cobre o caso do usuário voltar das Configurações após conceder a permissão manualmente.
        val granted = hasAllPermissions()
        hasPermissionState.value = granted
        if (granted) viewModel.refreshLibrary()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    @androidx.compose.runtime.Composable
    private fun rememberPermissionLauncher(onResult: (Boolean) -> Unit) =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results -> onResult(results.values.all { it }) }
}

@androidx.compose.runtime.Composable
private fun PermissionRequestScreen(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (permanentlyDenied) {
                    "Você negou permanentemente o acesso às músicas. Ative a permissão de Músicas e áudio nas Configurações do app."
                } else {
                    "O MusicFree precisa de acesso às suas músicas para funcionar."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            if (permanentlyDenied) {
                Button(onClick = onOpenSettings) {
                    Text("Abrir configurações")
                }
                OutlinedButton(onClick = onRequestPermission) {
                    Text("Tentar novamente")
                }
            } else {
                Button(onClick = onRequestPermission) {
                    Text("Permitir acesso")
                }
            }
        }
    }
}
