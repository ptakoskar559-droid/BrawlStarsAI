package com.brawlstarsai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var message by remember { mutableStateOf("Panel nakładki nie jest uruchomiony.") }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("BrawlStarsAI", style = MaterialTheme.typography.headlineMedium)
                        Text("Panel sterowania wyświetlany nad oryginalną grą.")
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                    !Settings.canDrawOverlays(this@MainActivity)) {
                                    message = "Zezwól BrawlStarsAI na wyświetlanie nad innymi aplikacjami, a następnie wróć tutaj i naciśnij przycisk ponownie."
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                } else {
                                    startService(Intent(this@MainActivity, FloatingPanelService::class.java))
                                    message = "Panel uruchomiony. Możesz otworzyć Brawl Stars."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Uruchom panel na ekranie") }
                        OutlinedButton(
                            onClick = {
                                stopService(Intent(this@MainActivity, FloatingPanelService::class.java))
                                message = "Panel został wyłączony."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Zamknij panel") }
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                        Text("Co działa teraz", style = MaterialTheme.typography.titleMedium)
                        Text("• Pływający panel nad innymi aplikacjami\n• Przełącznik ON/OFF i zamykanie panelu\n• Możliwość przenoszenia panelu po ekranie")
                        Text("Co będzie dodawane w kolejnych etapach", style = MaterialTheme.typography.titleMedium)
                        Text("• Przechwytywanie obrazu gry po Twojej zgodzie\n• Rozpoznawanie sytuacji na mapie\n• Moduł decyzji i sterowania — po osobnym skonfigurowaniu uprawnień")
                        Text(
                            "Uwaga: ON/OFF na tym etapie steruje stanem panelu, a nie postacią w grze.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}