package com.brawlstarsai

import android.content.Context

/**
 * First conservative closed-loop prototype.
 * It can auto-aim at a detected red health-bar candidate; it does not identify brawlers
 * and deliberately does not move until player-position detection is implemented.
 */
class BasicCombatPlanner : TacticalPlanner {
    override fun plan(state: WorldState, nowMillis: Long): AgentPlan {
        val target = state.entities
            .filter { it.type == EntityType.ENEMY && it.confidence >= 0.40f }
            .minByOrNull { entity ->
                val x = (entity.bounds.left + entity.bounds.right) / 2f
                val y = (entity.bounds.top + entity.bounds.bottom) / 2f
                kotlin.math.hypot(x - 0.5f, y - 0.5f)
            }

        if (target == null) {
            return AgentPlan(
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + 500L,
                action = AgentAction(AgentActionType.OBSERVE),
                confidence = 0.25f,
                rationale = "Brak wiarygodnego kandydata przeciwnika; obserwuj"
            )
        }

        val targetX = (target.bounds.left + target.bounds.right) / 2f
        // Health bars are above characters; aim slightly below the detected bar.
        val targetY = (target.bounds.bottom + 0.055f).coerceIn(0.05f, 0.95f)
        return AgentPlan(
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + 700L,
            action = AgentAction(
                type = AgentActionType.ATTACK,
                targetXNormalized = targetX,
                targetYNormalized = targetY,
                durationMillis = 180L
            ),
            confidence = target.confidence,
            rationale = "Auto-aim do kandydata czerwonego paska HP"
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
        if (nowMillis > plan.expiresAtMillis || nowMillis - state.updatedAtMillis > 1_000L) {
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
