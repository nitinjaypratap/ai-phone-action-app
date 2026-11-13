package com.example.aiphoneaction

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.core.content.ContextCompat

/** Owns start/stop requests for the user-consented capture foreground service. */
class CaptureController(context: Context) {
    private val appContext = context.applicationContext
    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(resultCode: Int, resultData: Intent): Boolean {
        return try {
            val intent = Intent(appContext, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(appContext, intent)
            isRunning = true
            true
        } catch (_: Exception) {
            isRunning = false
            false
        }
    }

    fun stop() {
        try {
            appContext.stopService(
                Intent(appContext, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_STOP)
            )
        } finally {
            isRunning = false
        }
    }

    fun createConsentIntent(): Intent {
        return (appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .createScreenCaptureIntent()
    }
}
