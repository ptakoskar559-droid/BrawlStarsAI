package com.brawlstarsai

import android.content.Context
import kotlin.math.hypot

/**
 * Closed-loop attack planner. Re-evaluates the current tracked target on every fresh
 * perception update and leads moving targets using their measured screen-space velocity.
 */
class BasicCombatPlanner(
    private val predictionHorizonSeconds: Float = 0.22f,
    private val minimumTrackConfidence: Float = 0.40f
) : TacticalPlanner {
    override fun plan(state: WorldState, nowMillis: Long): AgentPlan {
        val target = state.entities
            .filter {
                it.type == EntityType.ENEMY &&
                    it.confidence >= minimumTrackConfidence &&
                    nowMillis - it.lastSeenAtMillis <= 350L
            }
            .minByOrNull { entity ->
                val x = (entity.bounds.left + entity.bounds.right) / 2f
                val y = (entity.bounds.top + entity.bounds.bottom) / 2f
                hypot(x - 0.5f, y - 0.5f)
            }

        if (target == null) {
            return AgentPlan(
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + 250L,
                action = AgentAction(AgentActionType.OBSERVE),
                confidence = 0.25f,
                rationale = "Brak świeżego celu; ponowna obserwacja"
            )
        }

        val ageSeconds = ((nowMillis - target.lastSeenAtMillis).coerceAtLeast(0L) / 1_000f)
        val leadSeconds = (predictionHorizonSeconds - ageSeconds).coerceAtLeast(0f)
        val currentX = (target.bounds.left + target.bounds.right) / 2f
        val currentY = target.bounds.bottom + 0.055f
        val velocityX = target.velocityXPerSecond ?: 0f
        val velocityY = target.velocityYPerSecond ?: 0f
        val predictedX = (currentX + velocityX * leadSeconds).coerceIn(0.03f, 0.97f)
        val predictedY = (currentY + velocityY * leadSeconds).coerceIn(0.08f, 0.92f)

        return AgentPlan(
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + 220L,
            action = AgentAction(
                type = AgentActionType.ATTACK,
                targetXNormalized = predictedX,
                targetYNormalized = predictedY,
                durationMillis = 110L
            ),
            confidence = target.confidence,
            rationale = "Cel ${target.id}: korekta kierunku na podstawie ruchu"
        )
    }
}

/** Sends only fresh, high-confidence attack plans through the user-enabled Android service. */
class AccessibilityAgentDispatcher(private val context: Context) : AgentActionDispatcher {
    override fun dispatch(
        plan: AgentPlan,
        state: WorldState,
        nowMillis: Long
    ): ActionDispatchResult {
        if (!AgentControlState.isArmed()) {
            return ActionDispatchResult.Rejected("Sterowanie nieuzbrojone")
        }
        if (nowMillis > plan.expiresAtMillis || nowMillis - state.updatedAtMillis > 350L) {
            return ActionDispatchResult.Rejected("Nieaktualne dane lub plan")
        }
        if (plan.confidence < 0.40f) {
            return ActionDispatchResult.Rejected("Zbyt niska pewność detekcji")
        }
        val action = plan.action
        if (action.type != AgentActionType.ATTACK) {
            return ActionDispatchResult.Rejected("Plan nie zawiera akcji ataku")
        }
        val x = action.targetXNormalized ?: return ActionDispatchResult.Rejected("Brak celu X")
        val y = action.targetYNormalized ?: return ActionDispatchResult.Rejected("Brak celu Y")
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        val accepted = GameInputAccessibilityService.aimAndAttack(x, y, width, height, action.durationMillis)
        return if (accepted) ActionDispatchResult.Executed
        else ActionDispatchResult.Rejected("Usługa dostępności nie przyjęła gestu")
    }

    override fun cancelPendingActions() {
        AgentControlState.disarm("Anulowano oczekujące akcje")
    }
}
