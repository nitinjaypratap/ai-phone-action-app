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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class PhaseDActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var stateText: TextView
    private lateinit var recommendationText: TextView
    private lateinit var objectText: TextView
    private lateinit var captureToggle: CheckBox
    private lateinit var logText: TextView

    private val phaseTracker = PhaseConfidenceTracker()
    private val decisionEngine = FreeFireDecisionEngine()
    private val objectDetector by lazy { VisualObjectDetector() }
    private val projectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private var processingFrame = false
    private var lastFrameAt = 0L
    private var receiverRegistered = false

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCaptureService.ACTION_CAPTURED -> {
                    val path = intent.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
                    lastFrameAt = SystemClock.elapsedRealtime()
                    analyzeCapture(File(path))
                }
                ScreenCaptureService.ACTION_CAPTURE_ERROR -> {
                    statusText.text = intent.getStringExtra(ScreenCaptureService.EXTRA_ERROR) ?: "Capture error."
                    appendLog(statusText.text.toString())
                }
            }
        }
    }

    private val captureConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            captureToggle.isChecked = true
            statusText.text = "Continuous capture active (about 2 frames/second)."
            appendLog("Screen-sharing permission granted; live stream started.")
        } else {
            captureToggle.isChecked = false
            statusText.text = "Screen capture was not approved."
            appendLog("Capture permission denied.")
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
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
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
                if (checked && !isChangingConfigurations) startCaptureConsent()
                if (!checked) stopCapture()
            }
        }
        root.addView(captureToggle)
        root.addView(TextView(this).apply {
            text = "Capture is on-device and continuous until stopped. OCR phase confidence is heuristic; object labels are generic and do not identify enemies. Recommendations are informational—no automated gameplay actions are issued."
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

    private fun startCaptureConsent() {
        if (captureConsent.contract == null) return
        statusText.text = "Requesting Android screen-sharing approval…"
        captureConsent.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        phaseTracker.reset()
        processingFrame = false
        statusText.text = "Continuous capture stopped."
        appendLog("Capture stopped by user.")
    }

    private fun analyzeCapture(file: File) {
        if (processingFrame) {
            file.delete()
            return
        }
        processingFrame = true
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            processingFrame = false
            statusText.text = "Could not decode frame; waiting for the next one."
            return
        }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { ocr ->
                val observation = GameScreenAnalyzer().analyze(ocr.text)
                val estimate = phaseTracker.update(observation)
                statusText.text = "Analyzed frame; ${estimate.stableFrames} matching observations in recent window."
                stateText.text = "Phase: ${estimate.state} · confidence ${(estimate.confidence * 100).toInt()}%"
                objectDetector.detect(bitmap) { detections, error ->
                    val recommendation = decisionEngine.recommend(estimate, detections)
                    recommendationText.text = "${recommendation.headline}\n${recommendation.detail}"
                    objectText.text = if (detections.isEmpty()) {
                        "Generic visual detections: none${error?.let { " (detector: $it)" } ?: ""}"
                    } else {
                        "Generic visual detections: " + detections.take(5).joinToString { "${it.label} ${(it.confidence * 100).toInt()}%" }
                    }
                    appendLog("${estimate.state} (${(estimate.confidence * 100).toInt()}%); ${detections.size} generic object proposals.")
                    bitmap.recycle()
                    file.delete()
                    processingFrame = false
                }
            }
            .addOnFailureListener { error ->
                statusText.text = "OCR failed; waiting for next frame: ${error.localizedMessage ?: "unknown error"}"
                appendLog(statusText.text.toString())
                bitmap.recycle()
                file.delete()
                processingFrame = false
            }
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
        objectDetector.close()
        super.onDestroy()
    }
}
