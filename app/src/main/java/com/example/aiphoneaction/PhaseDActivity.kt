package com.example.aiphoneaction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var strategyText: TextView
    private lateinit var autoAssist: CheckBox
    private lateinit var logText: TextView

    private val pipeline = FramePipeline()
    private val strategyEngine = GameStrategyEngine()
    private val orchestrator by lazy { GameOrchestrator(this) }
    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
            analyzeCapture(File(path))
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
            statusText.text = "Capturing one frame…"
        } else {
            statusText.text = "Screen capture was not approved."
            appendLog("Capture permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "Phase D — assisted gameplay orchestrator"
            textSize = 21f
        })
        statusText = TextView(this).apply { text = "Ready — capture is user initiated."; textSize = 14f }
        root.addView(statusText)
        stateText = TextView(this).apply { text = "Game state: UNKNOWN"; textSize = 18f; setPadding(0, 10, 0, 0) }
        root.addView(stateText)
        recommendationText = TextView(this).apply { text = "Recommendation: Awaiting a frame"; setPadding(0, 8, 0, 0) }
        root.addView(recommendationText)
        strategyText = TextView(this).apply { text = "Strategy: Awaiting a frame"; setPadding(0, 8, 0, 8) }
        root.addView(strategyText)
        autoAssist = CheckBox(this).apply {
            text = "Enable limited assisted actions (movement / interact only)"
            isChecked = false
        }
        root.addView(autoAssist)
        root.addView(TextView(this).apply {
            text = "Assistance is opt-in and acts only after the same phase is detected in two captured frames. A fresh Android screen-share approval is needed for each capture. Combat, aiming, shooting, match queueing, and requeueing remain manual."
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })
        root.addView(Button(this).apply {
            text = "Capture and analyze one frame"
            setOnClickListener { captureConsent.launch(projectionManager.createScreenCaptureIntent()) }
        })
        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        logText = TextView(this).apply { text = "Activity log:\n" }
        scroll.addView(logText)
        root.addView(scroll)
        setContentView(root)
    }

    private fun analyzeCapture(file: File) {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            statusText.text = "Could not decode the captured frame."
            return
        }
        statusText.text = "Running on-device OCR…"
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { ocr ->
                val rawText = ocr.text.ifBlank { "No text detected." }
                val frame = pipeline.analyzeFrame(rawText)
                val plan = strategyEngine.plan(frame.inferredState)
                stateText.text = "Game state: ${frame.inferredState}"
                recommendationText.text = "Recommendation: ${frame.recommendation.primaryAction}\n${frame.recommendation.summary}"
                strategyText.text = "Plan: ${plan.goal}\n" + plan.actions.joinToString("\n") { "• ${it.label}: ${it.description}" }
                appendLog("OCR frame -> ${frame.inferredState}; ${rawText.take(180).replace('\n', ' ')}")
                orchestrator.observe(frame.inferredState, autoAssist.isChecked) { result ->
                    statusText.text = result.message
                    if (result.actionDispatched) appendLog("Assistance: ${result.message}")
                }
            }
            .addOnFailureListener { error ->
                statusText.text = "OCR failed: ${error.localizedMessage ?: "unknown error"}"
                appendLog(statusText.text.toString())
            }
            .addOnCompleteListener {
                bitmap.recycle()
                file.delete()
            }
    }

    private fun appendLog(message: String) {
        if (!::logText.isInitialized) return
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logText.append("[$timestamp] $message\n")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(captureReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
        super.onDestroy()
    }
}
