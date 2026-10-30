package com.brawlstarsai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var message by remember { mutableStateOf("Widzenie ekranu jest wyłączone.") }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("BrawlStarsAI", style = MaterialTheme.typography.headlineMedium)
                        Text("Panel nakładki i test przechwytywania ekranu.")
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    !Settings.canDrawOverlays(this@MainActivity)) {
                                    message = "Najpierw zezwól na wyświetlanie nad innymi aplikacjami."
                                    startActivity(Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName")
                                    ))
                                } else {
                                    startService(Intent(this@MainActivity, FloatingPanelService::class.java))
                                    message = "Panel uruchomiony."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Uruchom panel na ekranie") }

                        Button(
                            onClick = {
                                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                                message = "Oczekiwanie na zgodę Androida na przechwytywanie ekranu."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Włącz widzenie ekranu") }

                        OutlinedButton(
                            onClick = {
                                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                                message = "Przechwytywanie ekranu zatrzymane."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Wyłącz widzenie ekranu") }

                        OutlinedButton(
                            onClick = {
                                stopService(Intent(this@MainActivity, FloatingPanelService::class.java))
                                message = "Panel został wyłączony."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Zamknij panel") }

                        Text(message)
                        Text("Stan przechwytywania: " + if (ScreenVisionState.active) "AKTYWNE" else "WYŁĄCZONE")
                        Text("Odebrane klatki: " + ScreenVisionState.frameCount)
                        Text("Rozdzielczość: " + ScreenVisionState.dimensions)
                        HorizontalDivider()
                        Text("W tym etapie aplikacja odbiera klatki ekranu lokalnie. Nie rozpoznaje jeszcze postaci ani przeciwników i nie steruje grą.")
                    }
                }
            }
        }
    }
}