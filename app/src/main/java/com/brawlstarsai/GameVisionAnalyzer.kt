package com.brawlstarsai

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Lightweight heuristic detector for horizontal green/red health-bar-like regions.
 * This is a candidate detector, not a trained Brawl Stars object detector.
 */
object GameVisionAnalyzer {
    private data class Candidate(val x: Int, val y: Int, val width: Int, val color: String)

    fun analyze(source: Bitmap): String {
        val targetWidth = 360
        val targetHeight = (source.height * targetWidth.toFloat() / source.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            val candidates = mutableListOf<Candidate>()

            for (y in 0 until targetHeight) {
                var x = 0
                while (x < targetWidth) {
                    val color = barColor(pixels[y * targetWidth + x])
                    if (color == null) {
                        x++
                        continue
                    }
                    val start = x
                    var end = x
                    while (end < targetWidth && barColor(pixels[y * targetWidth + end]) == color) {
                        end++
                    }
                    val runWidth = end - start
                    if (runWidth in 9..78) {
                        candidates.add(Candidate((start + end) / 2, y, runWidth, color))
                    }
                    x = end
                }
            }

            val green = mergeRows(candidates.filter { it.color == "zielony" })
            val red = mergeRows(candidates.filter { it.color == "czerwony" })
            return "Paski podobne do HP • zielone: ${green.size}, czerwone: ${red.size}" +
                "\nZielone (x/y %): ${format(green, targetWidth, targetHeight)}" +
                "\nCzerwone (x/y %): ${format(red, targetWidth, targetHeight)}"
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    /** Returns coarse team-coloured health-bar candidates in normalized screen coordinates. */
    fun detectEntities(source: Bitmap): List<Detection> {
        val targetWidth = 360
        val targetHeight = (source.height * targetWidth.toFloat() / source.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        try {
            val pixels = IntArray(targetWidth * targetHeight)
            scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
            val candidates = mutableListOf<Candidate>()
            for (y in 0 until targetHeight) {
                var x = 0
                while (x < targetWidth) {
                    val color = barColor(pixels[y * targetWidth + x])
                    if (color == null) { x++; continue }
                    val start = x
                    var end = x
                    while (end < targetWidth && barColor(pixels[y * targetWidth + end]) == color) end++
                    val runWidth = end - start
                    if (runWidth in 9..78) candidates.add(Candidate((start + end) / 2, y, runWidth, color))
                    x = end
                }
            }
            val merged = mergeRows(candidates)
            return merged.map { item ->
                val halfWidth = item.width / 2f
                val type = if (item.color == "czerwony") EntityType.ENEMY else EntityType.ALLY
                Detection(
                    type = type,
                    bounds = BoundingBox(
                        left = ((item.x - halfWidth).coerceAtLeast(0f) / targetWidth).coerceIn(0f, 1f),
                        top = ((item.y - 3f).coerceAtLeast(0f) / targetHeight).coerceIn(0f, 1f),
                        right = ((item.x + halfWidth).coerceAtMost(targetWidth.toFloat()) / targetWidth).coerceIn(0f, 1f),
                        bottom = ((item.y + 3f).coerceAtMost(targetHeight.toFloat()) / targetHeight).coerceIn(0f, 1f)
                    ),
                    confidence = 0.45f
                )
            }
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun barColor(pixel: Int): String? {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return when {
            g > 125 && g > r * 1.35 && g > b * 1.20 && r < 150 -> "zielony"
            r > 145 && r > g * 1.35 && r > b * 1.25 -> "czerwony"
            else -> null
        }
    }

    private fun mergeRows(items: List<Candidate>): List<Candidate> {
        val groups = mutableListOf<MutableList<Candidate>>()
        for (item in items.sortedWith(compareBy<Candidate> { it.y }.thenBy { it.x })) {
            val group = groups.lastOrNull { previous ->
                val last = previous.last()
                abs(last.y - item.y) <= 3 && abs(last.x - item.x) <= 8
            }
            if (group != null) group.add(item) else groups.add(mutableListOf(item))
        }
        return groups.map { group ->
            Candidate(
                group.map { it.x }.average().toInt(),
                group.map { it.y }.average().toInt(),
                group.maxOf { it.width },
                group.first().color
            )
        }.filter { it.width >= 9 }.take(12)
    }

    private fun format(items: List<Candidate>, width: Int, height: Int): String {
        if (items.isEmpty()) return "brak"
        return items.joinToString("; ") {
            "${it.x * 100 / width}/${it.y * 100 / height}"
        }
    }
}
