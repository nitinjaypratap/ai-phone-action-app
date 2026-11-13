package com.example.aiphoneaction

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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** User-consented continuous capture. It only emits frames and never executes game actions. */
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_CAPTURED = "com.example.aiphoneaction.CAPTURED"
        const val ACTION_CAPTURE_ERROR = "com.example.aiphoneaction.CAPTURE_ERROR"
        const val ACTION_STOP = "com.example.aiphoneaction.STOP_CAPTURE"
        const val EXTRA_PATH = "capture_path"
        const val EXTRA_TIMESTAMP = "capture_timestamp"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_ERROR = "capture_error"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 10
        private const val FRAME_INTERVAL_MS = 500L
        private const val MAX_CAPTURE_WIDTH = 1280
        private const val FRAME_STALL_TIMEOUT_MS = 8_000L
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var lastFrameAt = 0L
    private var stallReported = false
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    private val stallWatchdog = object : Runnable {
        override fun run() {
            if (!running.get()) return
            val now = SystemClock.elapsedRealtime()
            if (lastFrameAt > 0L && now - lastFrameAt > FRAME_STALL_TIMEOUT_MS && !stallReported) {
                stallReported = true
                emitError("No screen frames received. Stop capture and grant screen-sharing permission again to recover.")
            }
            mainHandler.postDelayed(this, FRAME_STALL_TIMEOUT_MS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (running.get()) return START_NOT_STICKY

        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Phone Action")
            .setContentText("Screen sharing is active — stop it from the app at any time")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            emitError("Missing screen-capture consent result.")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startProjection(resultCode, resultData, startId)
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent, startId: Int) {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val scale = minOf(1f, MAX_CAPTURE_WIDTH.toFloat() / metrics.widthPixels)
            val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
            val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)

            captureThread = HandlerThread("AIPhoneFrameCapture").also { it.start() }
            val handler = Handler(captureThread!!.looper)
            captureHandler = handler
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            imageReader = reader
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = manager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("Android did not create a MediaProjection.")
            projection = newProjection
            running.set(true)
            newProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    val wasRunning = running.get()
                    stopCapture()
                    if (wasRunning) emitError("Screen sharing was stopped by Android or the user. Start a new capture to resume.")
                    stopSelf(startId)
                }
            }, handler)
            virtualDisplay = newProjection.createVirtualDisplay(
                "AIPhoneActionLiveCapture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            ) ?: throw IllegalStateException("Could not create the virtual display.")
            lastFrameAt = SystemClock.elapsedRealtime()
            stallReported = false
            reader.setOnImageAvailableListener({ source -> onFrameAvailable(source) }, handler)
            mainHandler.removeCallbacks(stallWatchdog)
            mainHandler.postDelayed(stallWatchdog, FRAME_STALL_TIMEOUT_MS)
        } catch (error: Exception) {
            emitError(error.localizedMessage ?: "Could not start screen capture.")
            stopCapture()
            stopSelf(startId)
        }
    }

    private fun onFrameAvailable(reader: ImageReader) {
        if (!running.get()) {
            reader.acquireLatestImage()?.close()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS) {
            reader.acquireLatestImage()?.close()
            return
        }
        val image = reader.acquireLatestImage() ?: return
        lastFrameAt = now
        stallReported = false
        try {
            val bitmap = imageToBitmap(image) ?: return
            val file = File(cacheDir, "live-frame-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }
            bitmap.recycle()
            pruneOldFrames()
            sendBroadcast(
                Intent(ACTION_CAPTURED).setPackage(packageName)
                    .putExtra(EXTRA_PATH, file.absolutePath)
                    .putExtra(EXTRA_TIMESTAMP, now)
            )
        } catch (error: Exception) {
            emitError(error.localizedMessage ?: "Frame capture failed.")
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        plane.buffer.apply { rewind() }.let(padded::copyPixelsFromBuffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    private fun pruneOldFrames() {
        cacheDir.listFiles()?.filter { it.name.startsWith("live-frame-") }
            ?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
    }

    private fun emitError(message: String) {
        sendBroadcast(Intent(ACTION_CAPTURE_ERROR).setPackage(packageName).putExtra(EXTRA_ERROR, message))
    }

    private fun stopCapture() {
        val wasActive = running.getAndSet(false)
        mainHandler.removeCallbacks(stallWatchdog)
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val oldProjection = projection
        projection = null
        // Clear our reference before stop(): MediaProjection invokes onStop synchronously on some devices.
        oldProjection?.stop()
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
        if (wasActive || oldProjection != null) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}
