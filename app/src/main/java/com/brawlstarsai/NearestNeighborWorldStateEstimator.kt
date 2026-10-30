package com.brawlstarsai

import kotlin.math.hypot

/**
 * Lightweight temporal association for heuristic detections.
 * It tracks candidate health bars only; it is not character identity recognition.
 */
class NearestNeighborWorldStateEstimator(
    private val maxMatchDistance: Float = 0.12f,
    private val staleAfterMillis: Long = 1_200L
) : WorldStateEstimator {
    private var nextId = 1L
    private var previous = emptyList<TrackedEntity>()

    override fun update(snapshot: PerceptionSnapshot): WorldState {
        val available = previous.toMutableList()
        val updated = snapshot.detections.map { detection ->
            val centerX = centerX(detection.bounds)
            val centerY = centerY(detection.bounds)
            val match = available
                .filter { it.type == detection.type }
                .map { entity -> entity to distance(centerX, centerY, centerX(entity.bounds), centerY(entity.bounds)) }
                .filter { it.second <= maxMatchDistance }
                .minByOrNull { it.second }
                ?.first

            if (match == null) {
                TrackedEntity(
                    id = nextId++,
                    type = detection.type,
                    bounds = detection.bounds,
                    confidence = detection.confidence,
                    lastSeenAtMillis = snapshot.capturedAtMillis
                )
            } else {
                available.remove(match)
                val elapsed = (snapshot.capturedAtMillis - match.lastSeenAtMillis).coerceAtLeast(1L) / 1_000f
                TrackedEntity(
                    id = match.id,
                    type = detection.type,
                    bounds = detection.bounds,
                    confidence = detection.confidence,
                    velocityXPerSecond = (centerX - centerX(match.bounds)) / elapsed,
                    velocityYPerSecond = (centerY - centerY(match.bounds)) / elapsed,
                    lastSeenAtMillis = snapshot.capturedAtMillis
                )
            }
        }

        val stillVisible = updated + available.filter {
            snapshot.capturedAtMillis - it.lastSeenAtMillis <= staleAfterMillis
        }
        previous = stillVisible
        return WorldState(
            updatedAtMillis = snapshot.capturedAtMillis,
            entities = stillVisible,
            hud = snapshot.hud,
            gameMode = snapshot.hud.gameMode
        )
    }

    private fun centerX(box: BoundingBox) = (box.left + box.right) / 2f
    private fun centerY(box: BoundingBox) = (box.top + box.bottom) / 2f
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float) = hypot(x1 - x2, y1 - y2)
}
