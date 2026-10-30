package com.brawlstarsai

/**
 * First, conservative decision layer.
 *
 * It does not control the game. It converts the currently available OCR and
 * color-bar heuristics into a human-readable recommendation. It deliberately
 * avoids claiming that a detected bar belongs to a specific player.
 */
object GameDecisionEngine {
    fun analyze(ocrText: String, visionSummary: String): String {
        val text = ocrText.lowercase()
        val redCount = extractCount(visionSummary, "czerwone")
        val greenCount = extractCount(visionSummary, "zielone")

        val mode = when {
            "brawl ball" in text || "piłka" in text -> "Tryb: Brawl Ball"
            "gem grab" in text || "klejnot" in text -> "Tryb: Gem Grab"
            "showdown" in text -> "Tryb: Showdown"
            "heist" in text || "sejf" in text -> "Tryb: Heist"
            "bounty" in text || "nagroda" in text -> "Tryb: Bounty"
            "hot zone" in text || "strefa" in text -> "Tryb: Hot Zone"
            else -> "Tryb: nierozpoznany"
        }

        val recommendation = when {
            redCount == null || greenCount == null ->
                "Decyzja: zbieram dane"
            redCount > 0 ->
                "Decyzja: możliwe zagrożenie — obserwuj otoczenie"
            greenCount > 0 ->
                "Decyzja: wykryto zielone obszary — kontynuuj obserwację"
            else ->
                "Decyzja: brak pewnych sygnałów — nie wykonuj ruchu automatycznie"
        }

        return "$mode\n$recommendation\nWykryte obszary: czerwone ${redCount ?: "?"}, zielone ${greenCount ?: "?"}"
    }

    private fun extractCount(summary: String, label: String): Int? {
        val match = Regex("$label:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(summary)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
