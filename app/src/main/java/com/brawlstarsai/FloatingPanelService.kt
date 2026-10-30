package com.brawlstarsai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: View? = null
    private var isAiEnabled = false
    private lateinit var status: TextView
    private lateinit var ocrStatus: TextView
    private lateinit var visionStatus: TextView
    private lateinit var decisionStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshOcr = object : Runnable {
        override fun run() {
            if (panel != null) {
                val text = ScreenVisionState.recognizedText
                ocrStatus.text = if (ScreenVisionState.active) {
                    "OCR • " + if (text.isBlank()) "analizuję..." else text.take(220)
                } else {
                    "OCR • widzenie wyłączone"
                }
                visionStatus.text = ScreenVisionState.objectSummary.take(260)
                decisionStatus.text = ScreenVisionState.decisionSummary.replace("\\\\n", "\\n").take(180)
                handler.postDelayed(this, 800L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    @Suppress("DEPRECATION")
    private fun showPanel() {
        if (panel != null) return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.rgb(28, 32, 42))
        }

        status = TextView(this).apply {
            text = "BrawlStarsAI • GOTOWY"
            setTextColor(Color.WHITE)
            textSize = 10f
        }
        ocrStatus = TextView(this).apply {
            text = "OCR • oczekiwanie"
            setTextColor(Color.WHITE)
            textSize = 9f
            maxLines = 3
            setPadding(0, 3, 0, 3)
        }
        val toggle = Button(this).apply {
            textSize = 10f
            minHeight = 0
            minimumHeight = 0
            setPadding(6, 0, 6, 0)
            text = "AI: OFF"
            setOnClickListener {
                isAiEnabled = !isAiEnabled
                text = if (isAiEnabled) "AI: ON" else "AI: OFF"
                status.text = if (isAiEnabled) "Tryb aktywny • moduł AI niepodłączony" else "Wstrzymano"
            }
        }
        val close = Button(this).apply {
            textSize = 10f
            minHeight = 0
            minimumHeight = 0
            setPadding(6, 0, 6, 0)
            text = "ZAMKNIJ PANEL"
            setOnClickListener { stopSelf() }
        }
        container.addView(status)
        container.addView(ocrStatus)
        visionStatus = TextView(this).apply {
            text = "Game Vision • oczekiwanie"
            setTextColor(Color.rgb(150, 230, 170))
            textSize = 8f
            maxLines = 2
            setPadding(0, 2, 0, 3)
        }
        container.addView(visionStatus)
        decisionStatus = TextView(this).apply {
            text = "Decyzja • oczekiwanie"
            setTextColor(Color.rgb(245, 210, 130))
            textSize = 9f
            maxLines = 3
            setPadding(0, 2, 0, 3)
        }
        container.addView(decisionStatus)
        container.addView(toggle)
        container.addView(close)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            (190 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(container, params)
                        return true
                    }
                }
                return false
            }
        })

        panel = container
        windowManager.addView(container, params)
        handler.post(refreshOcr)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) showPanel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshOcr)
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}