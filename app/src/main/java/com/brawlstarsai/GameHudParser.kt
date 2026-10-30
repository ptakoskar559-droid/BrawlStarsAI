package com.brawlstarsai

/** Extracts only explicit, high-signal HUD text. Missing or ambiguous values stay null. */
object GameHudParser {
    private val teamsPattern = Regex("(?:pozosta(?:ł|lo)\\s+dru(?:ż|z)yn|teams?\\s+left)\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val timerPattern = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    private val percentPattern = Regex("\\b(\\d{1,3})\\s*%")

    fun parse(rawText: String): HudState {
        val text = rawText.lowercase()
        val teams = teamsPattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val timer = timerPattern.find(text)?.let { match ->
            val minutes = match.groupValues[1].toIntOrNull()
            val seconds = match.groupValues[2].toIntOrNull()
            if (minutes != null && seconds != null && seconds < 60) minutes * 60 + seconds else null
        }
        val percentages = percentPattern.findAll(text)
            .mapNotNull { it.groupValues[1].toFloatOrNull() }
            .filter { it in 0f..100f }
            .distinct()
            .take(4)
            .toList()

        val mode = when {
            "napad" in text || "heist" in text || "sejf" in text -> "Napad"
            "brawl ball" in text || "piłka" in text -> "Brawl Ball"
            "gem grab" in text || "klejnot" in text -> "Gem Grab"
            "showdown" in text -> "Showdown"
            "bounty" in text || "nagroda" in text -> "Bounty"
            "hot zone" in text || "strefa" in text -> "Hot Zone"
            else -> null
        }
        val result = when {
            "zwycięstwo" in text || "zwyciestwo" in text || "victory" in text -> "WIN"
            "porażka" in text || "porazka" in text || "defeat" in text -> "LOSS"
            "remis" in text || "draw" in text -> "DRAW"
            else -> null
        }

        return HudState(
            remainingSeconds = timer,
            remainingTeams = teams,
            objectiveHealthPercentages = percentages,
            gameMode = mode,
            matchResult = result
        )
    }

    fun summary(state: HudState): String {
        val facts = mutableListOf<String>()
        state.gameMode?.let { facts += "tryb=" + it }
        state.remainingTeams?.let { facts += "pozostałe drużyny=" + it }
        state.remainingSeconds?.let { facts += "czas=" + (it / 60) + ":" + (it % 60).toString().padStart(2, '0') }
        if (state.objectiveHealthPercentages.isNotEmpty()) {
            facts += "procenty HUD=" + state.objectiveHealthPercentages.joinToString("/") { it.toInt().toString() + "%" }
        }
        state.matchResult?.let { facts += "wynik=" + it }
        return if (facts.isEmpty()) "HUD: brak pewnych danych tekstowych" else "HUD: " + facts.joinToString(" • ")
    }
}
