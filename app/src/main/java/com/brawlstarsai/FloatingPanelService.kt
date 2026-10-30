package com.brawlstarsai

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
            setPadding(14, 10, 14, 10)
            setBackgroundColor(Color.rgb(28, 32, 42))
        }

        status = TextView(this).apply {
            text = "BrawlStarsAI • GOTOWY"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val toggle = Button(this).apply {
            text = "AI: OFF"
            setOnClickListener {
                isAiEnabled = !isAiEnabled
                text = if (isAiEnabled) "AI: ON" else "AI: OFF"
                status.text = if (isAiEnabled) "Tryb aktywny • moduł AI niepodłączony" else "Wstrzymano"
            }
        }
        val close = Button(this).apply {
            text = "ZAMKNIJ PANEL"
            setOnClickListener { stopSelf() }
        }
        container.addView(status)
        container.addView(toggle)
        container.addView(close)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (panel == null) showPanel()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}