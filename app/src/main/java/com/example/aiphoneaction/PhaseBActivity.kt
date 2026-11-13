package com.example.aiphoneaction

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var logText: TextView
    private val pipeline = FramePipeline()

    private val handler = Handler(Looper.getMainLooper())
    private var liveLoop: Runnable? = null
    private var liveEnabled = false

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            text = "Phase B — live frame pipeline"
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
            setPadding(0, 8, 0, 12)
        }
        root.addView(recommendationText)

        root.addView(Button(this).apply {
            text = "Capture one frame"
            setOnClickListener { requestCapture() }
        })

        root.addView(Button(this).apply {
            text = "Start live loop"
            setOnClickListener { startLiveLoop() }
        })

        root.addView(Button(this).apply {
            text = "Stop live loop"
            setOnClickListener { stopLiveLoop() }
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
        capturePermission.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startLiveLoop() {
        if (liveEnabled) return
        liveEnabled = true
        appendLog("Live loop started.")
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
        appendLog("Live loop stopped.")
    }

    private fun appendLog(message: String) {
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logText.text = logText.text.toString() + "[$now] $message\n"
    }

    fun onFrameAnalyzed(rawText: String) {
        val result = pipeline.analyzeFrame(rawText)
        stateText.text = "State: ${result.inferredState.name}"
        recommendationText.text = "Recommendation: ${result.recommendation.primaryAction} | ${result.recommendation.summary}"
        appendLog("Frame analyzed. State=${result.inferredState.name}")
    }

    override fun onDestroy() {
        stopLiveLoop()
        super.onDestroy()
    }
}
