package com.example.aiphoneaction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class PhaseDActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var stateText: TextView
    private lateinit var recommendationText: TextView
    private lateinit var objectText: TextView
    private lateinit var captureToggle: CheckBox
    private lateinit var logText: TextView

    private val objectDetector by lazy { VisualObjectDetector() }
    private val frameProcessor by lazy {
        FrameProcessor(objectDetector) { result -> renderFrameResult(result) }
    }
    private val captureController by lazy { CaptureController(this) }
    private val projectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private var receiverRegistered = false
    private var changingToggleProgrammatically = false
    private var lastFrameAt = 0L

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCaptureService.ACTION_CAPTURED -> {
                    val path = intent.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
                    lastFrameAt = SystemClock.elapsedRealtime()
                    frameProcessor.submit(File(path))
                }
                ScreenCaptureService.ACTION_CAPTURE_ERROR -> {
                    val message = intent.getStringExtra(ScreenCaptureService.EXTRA_ERROR) ?: "Capture error."
                    statusText.text = message
                    appendLog(message)
                    if (message.contains("stopped", ignoreCase = true) || message.contains("No screen frames", ignoreCase = true)) {
                        setCaptureChecked(false)
                    }
                }
            }
        }
    }

    private val captureConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null && captureController.start(result.resultCode, data)) {
            setCaptureChecked(true)
            statusText.text = "Continuous capture active (throttled to about 2 frames/second)."
            appendLog("Screen-sharing permission granted; capture started.")
        } else {
            setCaptureChecked(false)
            statusText.text = "Capture did not start. Grant screen sharing again to retry."
            appendLog("Capture permission denied or service failed to start.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerCaptureReceiver()
        buildUi()
    }

    private fun registerCaptureReceiver() {
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_CAPTURED)
            addAction(ScreenCaptureService.ACTION_CAPTURE_ERROR)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Live screen understanding — supervised"
            textSize = 21f
        })
        statusText = TextView(this).apply { text = "Ready. Start capture to share the screen with this app."; textSize = 14f }
        root.addView(statusText)
        stateText = TextView(this).apply { text = "Phase: UNKNOWN · confidence 0%"; textSize = 18f; setPadding(0, 10, 0, 0) }
        root.addView(stateText)
        recommendationText = TextView(this).apply { text = "Free Fire guidance: waiting for frames"; setPadding(0, 8, 0, 0) }
        root.addView(recommendationText)
        objectText = TextView(this).apply { text = "Generic visual detections: none"; setPadding(0, 8, 0, 8) }
        root.addView(objectText)
        captureToggle = CheckBox(this).apply {
            text = "Continuous screen capture (user-consented)"
            isChecked = false
            setOnCheckedChangeListener { _, checked ->
                if (changingToggleProgrammatically) return@setOnCheckedChangeListener
                if (checked) {
                    statusText.text = "Requesting Android screen-sharing approval…"
                    captureConsent.launch(captureController.createConsentIntent())
                } else if (captureController.isRunning) {
                    stopCapture()
                }
            }
        }
        root.addView(captureToggle)
        root.addView(TextView(this).apply {
            text = "Capture runs on-device until stopped. OCR confidence is a heuristic; generic object labels do not identify enemies. Recommendations are informational and issue no game actions."
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        logText = TextView(this).apply { text = "Live log:\n" }
        scroll.addView(logText)
        root.addView(scroll)
        setContentView(root)
    }

    private fun setCaptureChecked(checked: Boolean) {
        changingToggleProgrammatically = true
        captureToggle.isChecked = checked
        changingToggleProgrammatically = false
    }

    private fun stopCapture() {
        captureController.stop()
        frameProcessor.resetConfidence()
        setCaptureChecked(false)
        stateText.text = "Phase: UNKNOWN · confidence 0%"
        statusText.text = "Capture stopped. Start again and approve screen sharing to resume."
        appendLog("Capture stopped by user.")
    }

    private fun renderFrameResult(result: FrameProcessor.Result) {
        if (result.ocrError != null) {
            statusText.text = "Frame analysis failed (one bounded retry was used); waiting for next frame."
            stateText.text = "Phase: UNKNOWN · confidence 0%"
            recommendationText.text = result.ocrError
            appendLog("Frame analysis error: ${result.ocrError}")
            return
        }
        val estimate = result.estimate
        val recommendation = FreeFireDecisionEngine().recommend(estimate, result.detections)
        statusText.text = "Frame analyzed at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}; capture continues."
        stateText.text = "Phase: ${estimate.state} · confidence ${(estimate.confidence * 100).toInt()}% · evidence ${estimate.stableFrames}"
        recommendationText.text = "${recommendation.headline}\n${recommendation.detail}"
        objectText.text = if (result.detections.isEmpty()) {
            "Generic visual detections: none${result.objectError?.let { " ($it)" } ?: ""}"
        } else {
            "Generic visual detections: " + result.detections.take(5).joinToString { "${it.label} ${(it.confidence * 100).toInt()}%" }
        }
        appendLog("${estimate.state}, confidence ${(estimate.confidence * 100).toInt()}%, objects ${result.detections.size}.")
    }

    private fun appendLog(message: String) {
        if (!::logText.isInitialized) return
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logText.append("[$timestamp] $message\n")
        val lines = logText.text.lines()
        if (lines.size > 80) logText.text = lines.takeLast(80).joinToString("\n")
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(captureReceiver)
        frameProcessor.close()
        if (isFinishing && captureController.isRunning) captureController.stop()
        super.onDestroy()
    }
}
