package com.brawlstarsai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-enabled Android accessibility input adapter.
 * It does not bypass Android permissions; dispatch is disabled until explicitly armed in-app.
 */
object AgentControlState {
    private val armed = AtomicBoolean(false)
    @Volatile var lastInputStatus: String = "Sterowanie wyłączone"

    fun isArmed(): Boolean = armed.get()

    fun arm() {
        armed.set(true)
        lastInputStatus = "Sterowanie uzbrojone"
    }

    fun disarm(reason: String = "Sterowanie zatrzymane") {
        armed.set(false)
        lastInputStatus = reason
    }
}

class GameInputAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var instance: GameInputAccessibilityService? = null

        fun isConnected(): Boolean = instance != null

        fun tap(x: Float, y: Float, durationMillis: Long = 70L): Boolean =
            instance?.dispatchLine(x, y, x, y, durationMillis) ?: false

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMillis: Long = 350L): Boolean =
            instance?.dispatchLine(x1, y1, x2, y2, durationMillis) ?: false

        /** Brawl Stars landscape layout: drag the attack control toward the detected target. */
        fun aimAndAttack(
            targetXNormalized: Float,
            targetYNormalized: Float,
            screenWidth: Float,
            screenHeight: Float,
            durationMillis: Long
        ): Boolean {
            val service = instance ?: return false
            val startX = screenWidth * 0.85f
            val startY = screenHeight * 0.64f
            return service.dispatchLine(
                startX,
                startY,
                targetXNormalized.coerceIn(0f, 1f) * screenWidth,
                targetYNormalized.coerceIn(0f, 1f) * screenHeight,
                durationMillis
            )
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentControlState.lastInputStatus = "Sterowanie Android połączone; domyślnie wyłączone"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        AgentControlState.disarm("Przerwano sterowanie")
    }

    private fun dispatchLine(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMillis: Long
    ): Boolean {
        if (!AgentControlState.isArmed()) {
            AgentControlState.lastInputStatus = "Odrzucono wejście: agent nie jest uzbrojony"
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            AgentControlState.lastInputStatus = "Gesty wymagają Androida 7 lub nowszego"
            return false
        }
        val display = resources.displayMetrics
        val sx = startX.coerceIn(0f, (display.widthPixels - 1).toFloat())
        val sy = startY.coerceIn(0f, (display.heightPixels - 1).toFloat())
        val ex = endX.coerceIn(0f, (display.widthPixels - 1).toFloat())
        val ey = endY.coerceIn(0f, (display.heightPixels - 1).toFloat())
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMillis.coerceIn(40L, 800L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                AgentControlState.lastInputStatus = "Gest wykonany"
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                AgentControlState.lastInputStatus = "Gest anulowany"
            }
        }, mainHandler)
        if (!accepted) AgentControlState.lastInputStatus = "Android odrzucił gest"
        return accepted
    }

    override fun onDestroy() {
        AgentControlState.disarm("Usługa sterowania rozłączona")
        if (instance === this) instance = null
        super.onDestroy()
    }
}
