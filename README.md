package com.example.aiphoneaction

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PhaseBActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var stateText: TextView
    private lateinit var recommendationText: TextView
    private lateinit var strategyText: TextView
    private lateinit var logText: TextView

    private val pipeline = FramePipeline()
    private val strategyEngine = GameStrategyEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var liveLoop: Runnable? = null
    private var liveEnabled = false

    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            appendLog("Capture permission accepted; service started.")
        } else {
            appendLog("Capture permission denied.")
        }
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
            val file = java.io.File(path)
            if (!file.exists()) {
                appendLog("Captured file missing: $path")
                return
            }
            analyzeCapturedImage(file)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerCaptureReceiver()
        buildUi()
    }

    private fun registerCaptureReceiver() {
        val filter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, filter)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            text = "Phase C — tactical strategy engine"
            textSize = 20f
        }
        root.addView(statusText)

        stateText = TextView(this).apply {
            text = "State: UNKNOWN"
            textSize = 16f
            setPadding(0, 12, 0, 0)
        }
        root.addView(stateText)

        recommendationText = TextView(this).apply {
            text = "Recommendation: Awaiting first frame"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        root.addView(recommendationText)

        strategyText = TextView(this).apply {
            text = "Strategy: Awaiting tactical plan"
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }
        root.addView(strategyText)

        root.addView(Button(this).apply {
            text = "Capture one frame"
            setOnClickListener { requestCapture() }
        })

        root.addView(Button(this).apply {
            text = "Start live tactical loop"
            setOnClickListener { startLiveLoop() }
        })

        root.addView(Button(this).apply {
            text = "Stop live tactical loop"
            setOnClickListener { stopLiveLoop() }
        })

        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        logText = TextView(this).apply {
            text = "Log:\n"
            gravity = Gravity.START
        }
        logScroll.addView(logText)
        root.addView(logScroll)

        setContentView(root)
    }

    private fun requestCapture() {
        appendLog("Requesting screen capture permission...")
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        capturePermission.launch(manager.createScreenCaptureIntent())
    }

    private fun startLiveLoop() {
        if (liveEnabled) return
        liveEnabled = true
        appendLog("Live tactical loop started.")
        liveLoop = object : Runnable {
            override fun run() {
                if (!liveEnabled) return
                requestCapture()
                handler.postDelayed(this, 2500L)
            }
        }
        handler.postDelayed(liveLoop!!, 2500L)
    }

    private fun stopLiveLoop() {
        liveEnabled = false
        liveLoop?.let { handler.removeCallbacks(it) }
        liveLoop = null
        appendLog("Live tactical loop stopped.")
    }

    private fun analyzeCapturedImage(file: java.io.File) {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            appendLog("Could not decode captured image.")
            file.delete()
            return
        }

        com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val rawText = result.text.ifBlank { "No text detected." }
                val resultData = pipeline.analyzeFrame(rawText)
                val plan = strategyEngine.plan(resultData.inferredState)

                stateText.text = "State: ${resultData.inferredState.name}"
                recommendationText.text = "Recommendation: ${resultData.recommendation.primaryAction} | ${resultData.recommendation.summary}"
                strategyText.text = "Strategy: ${plan.goal}\n" + plan.actions.joinToString(separator = "\n") { "- ${it.label}: ${it.description}" }
                appendLog("State detected: ${resultData.inferredState.name}; goal=${plan.goal}")
            }
            .addOnFailureListener { error ->
                appendLog("OCR failed: ${error.localizedMessage}")
            }
            .addOnCompleteListener {
                bitmap.recycle()
                file.delete()
            }
    }

    private fun appendLog(message: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logText.text = "${logText.text}[$stamp] $message\n"
    }

    override fun onDestroy() {
        stopLiveLoop()
        unregisterReceiver(captureReceiver)
        super.onDestroy()
    }
}
