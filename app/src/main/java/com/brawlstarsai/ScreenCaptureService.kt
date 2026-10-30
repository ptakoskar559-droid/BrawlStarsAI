package com.brawlstarsai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

object ScreenVisionState {
    @Volatile var active: Boolean = false
    @Volatile var frameCount: Long = 0
    @Volatile var dimensions: String = "—"
}

class ScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val CHANNEL_ID = "brawlstarsai_screen_capture"
        private const val NOTIFICATION_ID = 42
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val density = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage()
            if (image != null) {
                ScreenVisionState.frameCount += 1
                image.close()
            }
        }, null)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)

        virtualDisplay = projection?.createVirtualDisplay(
            "BrawlStarsAI-Screen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )

        ScreenVisionState.frameCount = 0
        ScreenVisionState.dimensions = "${width} × ${height}"
        ScreenVisionState.active = virtualDisplay != null
        if (!ScreenVisionState.active) stopSelf()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID,
                "Przechwytywanie ekranu",
                NotificationManager.IMPORTANCE_LOW
            ))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("BrawlStarsAI")
            .setContentText("Przechwytywanie ekranu jest aktywne")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        ScreenVisionState.active = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}