package com.brawlstarsai

/**
 * Conservative decision layer based on OCR text and heuristic color regions.
 * Color regions are not player detections and must never be reported as confirmed threats.
 */
object GameDecisionEngine {
    fun analyze(ocrText: String, visionSummary: String): String {
        val text = ocrText.lowercase()
        val redCount = extractCount(visionSummary, "czerwone")
        val greenCount = extractCount(visionSummary, "zielone")
        val fullBaseBars = Regex("100\\s*%").findAll(text).count()

        val mode = when {
            "napad" in text || "heist" in text || "sejf" in text ->
                "Tryb: Napad"
            "brawl ball" in text || "piłka" in text -> "Tryb: Brawl Ball"
            "gem grab" in text || "klejnot" in text -> "Tryb: Gem Grab"
            "showdown" in text -> "Tryb: Showdown"
            "bounty" in text || "nagroda" in text -> "Tryb: Bounty"
            "hot zone" in text || "strefa" in text -> "Tryb: Hot Zone"
            fullBaseBars >= 2 ->
                "Tryb: prawdopodobnie Napad (dwa paski baz)"
            else -> "Tryb: nierozpoznany"
        }

        val observation = when {
            redCount == null || greenCount == null ->
                "Analiza: zbieram dane"
            redCount > 0 ->
                "Uwaga: czerwone obszary ($redCount), niepotwierdzone jako przeciwnicy"
            greenCount > 0 ->
                "Analiza: zielone obszary ($greenCount), bez identyfikacji graczy"
            else ->
                "Analiza: brak pewnych sygnałów"
        }

        return "$mode\\n$observation\\nWykryte obszary: czerwone ${redCount ?: "?"}, zielone ${greenCount ?: "?"}"
    }

    private fun extractCount(summary: String, label: String): Int? {
        val match = Regex("$label:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(summary)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
