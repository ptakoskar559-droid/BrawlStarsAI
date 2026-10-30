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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
                var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                var recognizedText by remember { mutableStateOf(ScreenVisionState.recognizedText) }
                LaunchedEffect(Unit) {
                    while (true) {
                        preview = ScreenVisionState.latestFrame
                        recognizedText = ScreenVisionState.recognizedText
                        delay(500)
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
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
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                message = "W ustawieniach Androida włącz usługę BrawlStarsAI."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Skonfiguruj sterowanie Android") }

                        Button(
                            onClick = {
                                if (GameInputAccessibilityService.isConnected()) {
                                    AgentControlState.arm()
                                    message = "Auto-aim uzbrojony. Włącz widzenie ekranu i testuj w przyjaznym meczu."
                                } else {
                                    message = "Najpierw włącz usługę BrawlStarsAI w ustawieniach dostępności Androida."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !AgentControlState.isArmed()
                        ) { Text("Uzbrój sterowanie") }

                        OutlinedButton(
                            onClick = {
                                AgentControlState.disarm("Ręczne zatrzymanie")
                                message = "Sterowanie natychmiast wyłączone."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("AWARYJNE STOP") }

                        Text("Sterowanie: " + AgentControlState.lastInputStatus)
                        Text("Prototyp: auto-aim do wykrytych czerwonych pasków HP. Ruch postaci nie jest jeszcze sterowany.")

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
                        Text("Odczyt tekstu z gry (OCR):", style = MaterialTheme.typography.titleMedium)
                        Text(recognizedText)
                        Text("Podgląd przechwyconego obrazu:", style = MaterialTheme.typography.titleMedium)
                        if (preview != null) {
                            Image(
                                bitmap = preview!!.asImageBitmap(),
                                contentDescription = "Ostatnia przechwycona klatka ekranu",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text("Brak klatki podglądu. Włącz widzenie ekranu i poczekaj chwilę.")
                        }
                        HorizontalDivider()
                        Text("Aplikacja odczytuje tekst z obrazu lokalnie. Nie rozpoznaje jeszcze postaci ani przeciwników i nie steruje grą.")
                    }
                }
            }
        }
    }
}
