package com.brawlstarsai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object ScreenVisionState {
    @Volatile var active: Boolean = false
    @Volatile var frameCount: Long = 0
    @Volatile var dimensions: String = "—"
    @Volatile var latestFrame: Bitmap? = null
    @Volatile var recognizedText: String = "Oczekiwanie na obraz..."
    @Volatile var objectSummary: String = "Oczekiwanie na analizę obrazu..."
    @Volatile var decisionSummary: String = "Decyzja: oczekiwanie na dane"
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
    private var lastPreviewAt: Long = 0L
    private var lastOcrAt: Long = 0L
    private var lastVisionAt: Long = 0L
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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
                try {
                    ScreenVisionState.frameCount += 1
                    val now = SystemClock.elapsedRealtime()
                    // Keep the live preview lightweight: create at most two preview bitmaps per second.
                    if (now - lastPreviewAt >= 500L) {
                        imageToBitmap(image)?.let { frame ->
                            ScreenVisionState.latestFrame = frame
                            if (now - lastOcrAt >= 1000L) {
                                runTextRecognition(frame)
                                lastOcrAt = now
                            }
                            if (now - lastVisionAt >= 1000L) {
                                ScreenVisionState.objectSummary = GameVisionAnalyzer.analyze(frame)
                                ScreenVisionState.decisionSummary = GameDecisionEngine.analyze(
                                    ScreenVisionState.recognizedText,
                                    ScreenVisionState.objectSummary
                                )
                                lastVisionAt = now
                            }
                        }
                        lastPreviewAt = now
                    }
                } finally {
                    image.close()
                }
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
        ScreenVisionState.latestFrame = null
        ScreenVisionState.recognizedText = "Analiza tekstu uruchamia się..."
        ScreenVisionState.objectSummary = "Analiza obrazu uruchamia się..."
        ScreenVisionState.decisionSummary = "Decyzja: oczekiwanie na dane"
        ScreenVisionState.dimensions = "${width} × ${height}"
        ScreenVisionState.active = virtualDisplay != null
        if (!ScreenVisionState.active) stopSelf()
    }

    private fun runTextRecognition(frame: Bitmap) {
        // OCR runs on a reduced copy to limit CPU and memory use on the phone.
        val ocrWidth = (frame.width / 2).coerceAtLeast(1)
        val ocrHeight = (frame.height / 2).coerceAtLeast(1)
        val ocrBitmap = Bitmap.createScaledBitmap(frame, ocrWidth, ocrHeight, true)
        textRecognizer.process(InputImage.fromBitmap(ocrBitmap, 0))
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                ScreenVisionState.recognizedText =
                    if (text.isBlank()) "Nie wykryto tekstu na ostatniej klatce." else text
            }
            .addOnFailureListener { error ->
                ScreenVisionState.recognizedText = "Błąd OCR: ${error.localizedMessage ?: "nieznany"}"
            }
            .addOnCompleteListener {
                if (ocrBitmap !== frame && !ocrBitmap.isRecycled) ocrBitmap.recycle()
            }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null
        val paddedWidth = rowStride / pixelStride
        if (paddedWidth < image.width) return null

        buffer.rewind()
        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)
        val result = if (paddedWidth == image.width) {
            paddedBitmap
        } else {
            Bitmap.createBitmap(paddedBitmap, 0, 0, image.width, image.height).also {
                paddedBitmap.recycle()
            }
        }
        return result
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
        ScreenVisionState.latestFrame = null
        ScreenVisionState.recognizedText = "Przechwytywanie wyłączone."
        ScreenVisionState.objectSummary = "Przechwytywanie wyłączone."
        ScreenVisionState.decisionSummary = "Decyzja: przechwytywanie wyłączone"
        textRecognizer.close()
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
