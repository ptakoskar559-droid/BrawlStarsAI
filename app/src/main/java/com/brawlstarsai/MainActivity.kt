package com.brawlstarsai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Lesson(val command: String, val result: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CommandCenter() } } }
    }
}

@Composable
private fun CommandCenter() {
    var command by remember { mutableStateOf("") }
    var observation by remember { mutableStateOf("") }
    var recommendation by remember { mutableStateOf("Czekam na opis sytuacji z gry.") }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    val lessons = remember { mutableStateListOf<Lesson>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("BrawlStarsAI", style = MaterialTheme.typography.headlineMedium)
            Text("Panel dowodzenia • wersja 0.1.0")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Status systemu", style = MaterialTheme.typography.titleMedium)
                    Text("Gotowy do analizy. Sterowanie grą nie jest jeszcze podłączone.")
                    Text("Tryb: każda decyzja wymaga Twojego zatwierdzenia.")
                }
            }
        }
        item {
            Text("Obserwacja sytuacji", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = observation, onValueChange = { observation = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Co widzisz na ekranie?") },
                minLines = 3
            )
            Button(onClick = {
                recommendation = if (observation.isBlank()) {
                    "Dodaj opis sytuacji, aby przygotować analizę."
                } else {
                    "Opis zapisany. Wersja 0.1 nie rozpoznaje jeszcze obrazu automatycznie. Oceń pozycje, zdrowie, amunicję i zagrożenia, a następnie wpisz polecenie."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Przeanalizuj opis") }
        }
        item {
            Text("Rekomendacja", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) { Text(recommendation, Modifier.padding(16.dp)) }
        }
        item {
            Text("Twoja komenda", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = command, onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Np. podejdź za ścianę i nie atakuj") }, minLines = 2
            )
            Button(onClick = {
                if (command.isNotBlank()) {
                    pendingCommand = command.trim()
                    recommendation = "Polecenie oczekuje na zatwierdzenie: $pendingCommand"
                }
            }, modifier = Modifier.fillMaxWidth(),
                enabled = command.isNotBlank() && pendingCommand == null
            ) { Text("Przygotuj polecenie") }
        }
        item {
            if (pendingCommand != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Oczekuje na Twoją zgodę", style = MaterialTheme.typography.titleMedium)
                        Text(pendingCommand.orEmpty())
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                lessons.add(Lesson(pendingCommand.orEmpty(),
                                    "Zatwierdzono ręcznie; wykonanie w grze nie jest jeszcze podłączone."))
                                pendingCommand = null
                                command = ""
                                recommendation = "Decyzja zatwierdzona i zapisana w dzienniku."
                            }) { Text("Zatwierdź") }
                            OutlinedButton(onClick = {
                                pendingCommand = null
                                recommendation = "Polecenie odrzucone. Czekam na nowe."
                            }) { Text("Odrzuć") }
                        }
                    }
                }
            }
        }
        item {
            Text("Dziennik doświadczeń", style = MaterialTheme.typography.titleMedium)
            if (lessons.isEmpty()) Text("Brak zapisanych decyzji.")
        }
        items(lessons.reversed()) { lesson ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Polecenie: ${lesson.command}")
                    Text(lesson.result, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(
                "To pierwszy szkielet aplikacji. Nie przechwytuje jeszcze ekranu gry, nie steruje postacią i nie trenuje modelu AI. Te moduły dodamy etapami.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
