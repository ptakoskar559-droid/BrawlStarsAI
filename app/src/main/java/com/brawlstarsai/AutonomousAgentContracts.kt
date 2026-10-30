package com.brawlstarsai

/** Domain contracts for the autonomous agent. These types deliberately avoid Android APIs. */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init { require(left <= right && top <= bottom) { "Invalid bounding box" } }
}

enum class EntityType {
    PLAYER, ALLY, ENEMY, PROJECTILE, OBSTACLE, BUSH, OBJECTIVE, UNKNOWN
}

data class Detection(
    val type: EntityType,
    val bounds: BoundingBox,
    val confidence: Float,
    val trackId: Long? = null
) {
    init { require(confidence in 0f..1f) { "Confidence must be between 0 and 1" } }
}

data class HudState(
    val healthPercent: Float? = null,
    val ammo: Int? = null,
    val superReady: Boolean? = null,
    val remainingSeconds: Int? = null,
    val objectiveHealthPercent: Float? = null,
    val remainingTeams: Int? = null,
    val objectiveHealthPercentages: List<Float> = emptyList(),
    val gameMode: String? = null,
    val matchResult: String? = null
) {
    init {
        require(healthPercent == null || healthPercent in 0f..100f)
        require(objectiveHealthPercent == null || objectiveHealthPercent in 0f..100f)
        require(ammo == null || ammo >= 0)
        require(remainingSeconds == null || remainingSeconds >= 0)
        require(remainingTeams == null || remainingTeams >= 0)
        require(objectiveHealthPercentages.all { it in 0f..100f })
    }
}

data class PerceptionSnapshot(
    val capturedAtMillis: Long,
    val detections: List<Detection>,
    val hud: HudState,
    val recognizedText: String = ""
)

data class TrackedEntity(
    val id: Long,
    val type: EntityType,
    val bounds: BoundingBox,
    val confidence: Float,
    val velocityXPerSecond: Float? = null,
    val velocityYPerSecond: Float? = null,
    val lastSeenAtMillis: Long
)

data class WorldState(
    val updatedAtMillis: Long,
    val entities: List<TrackedEntity>,
    val hud: HudState,
    val gameMode: String? = null,
    val objective: String? = null
)

enum class AgentActionType {
    OBSERVE, MOVE, AIM, ATTACK, USE_SUPER, WAIT, STOP
}

data class AgentAction(
    val type: AgentActionType,
    val targetXNormalized: Float? = null,
    val targetYNormalized: Float? = null,
    val durationMillis: Long = 0L
) {
    init {
        require(targetXNormalized == null || targetXNormalized in 0f..1f)
        require(targetYNormalized == null || targetYNormalized in 0f..1f)
        require(durationMillis >= 0L)
    }
}

data class AgentPlan(
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val action: AgentAction,
    val confidence: Float,
    val rationale: String
) {
    init {
        require(expiresAtMillis >= createdAtMillis)
        require(confidence in 0f..1f)
    }
}

sealed interface ActionDispatchResult {
    data object Executed : ActionDispatchResult
    data class Rejected(val reason: String) : ActionDispatchResult
    data object Cancelled : ActionDispatchResult
}

enum class AgentRunState { STOPPED, STARTING, OBSERVING, PLANNING, EXECUTING, PAUSED, ERROR }

/** Ports are implemented by Android-specific adapters in later phases. */
interface ObjectPerception {
    fun analyze(frame: Any, capturedAtMillis: Long): PerceptionSnapshot
}

interface WorldStateEstimator {
    fun update(snapshot: PerceptionSnapshot): WorldState
}

interface TacticalPlanner {
    fun plan(state: WorldState, nowMillis: Long): AgentPlan
}

interface AgentActionDispatcher {
    fun dispatch(plan: AgentPlan, state: WorldState, nowMillis: Long): ActionDispatchResult
    fun cancelPendingActions()
}