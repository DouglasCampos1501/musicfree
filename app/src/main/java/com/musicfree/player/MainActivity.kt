package com.musicfree.player

import android.Manifest
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var hasPermission by remember { mutableStateOf(hasAllPermissions()) }

            val permissionLauncher = rememberPermissionLauncher { granted ->
                hasPermission = granted
                if (granted) viewModel.refreshLibrary()
            }

            MusicFreeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (hasPermission) {
                        AppNavGraph(viewModel = viewModel)
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = { permissionLauncher.launch(requiredPermissions) }
                        )
                    }
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @androidx.compose.runtime.Composable
    private fun rememberPermissionLauncher(onResult: (Boolean) -> Unit) =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results -> onResult(results.values.all { it }) }
}

@androidx.compose.runtime.Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "O MusicFree precisa de acesso às suas músicas para funcionar.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onRequestPermission) {
                Text("Permitir acesso")
            }
        }
    }
}
