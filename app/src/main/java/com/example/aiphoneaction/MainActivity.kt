package com.example.aiphoneaction

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var phaseText: TextView
    private lateinit var decisionText: TextView
    private lateinit var commandInput: EditText

    private val analyzer = GameScreenAnalyzer()
    private val decisionEngine = GameDecisionEngine()
    private val monitorHandler = Handler(Looper.getMainLooper())
    private var liveMonitoring = false
    private var monitorRunnable: Runnable? = null

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val path = intent?.getStringExtra(ScreenCaptureService.EXTRA_PATH) ?: return
            recognizeText(File(path))
        }
    }

    private val projectionConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, intent)
            status.text = "Capturing screen..."
        } else {
            status.text = "Screen capture was not approved."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerCaptureReceiver()
        buildUi()
    }

    private fun registerCaptureReceiver() {
        val receiverFilter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, receiverFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, receiverFilter)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "AI Phone Action — gameplay dashboard"
            textSize = 22f
        })

        root.addView(TextView(this).apply {
            text = "Live OCR, game-state inference, tactical suggestions, and command automation."
            textSize = 14f
            setPadding(0, 10, 0, 12)
        })

        status = TextView(this).apply {
            text = "Ready"
            textSize = 14f
        }
        root.addView(status)

        phaseText = TextView(this).apply {
            text = "Game state: UNKNOWN"
            textSize = 16f
        }
        root.addView(phaseText)

        decisionText = TextView(this).apply {
            text = "Recommendation: Awaiting first capture."
            textSize = 14f
        }
        root.addView(decisionText)

        root.addView(button("Enable touch actions") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(button("Capture one frame") {
            requestScreenCapture()
        })

        root.addView(button("Start live monitoring") {
            startLiveMonitoring()
        })

        root.addView(button("Stop live monitoring") {
            stopLiveMonitoring()
        })

        root.addView(TextView(this).apply {
            text = "Manual automation command"
            setPadding(0, 18, 0, 0)
        })

        commandInput = EditText(this).apply {
            setText("tap 250 500; wait 500; swipe 250 500 900 1100")
            minLines = 3
            setSingleLine(false)
        }
        root.addView(commandInput)

        root.addView(button("Run command") {
            val script = AutomationParser.parse(commandInput.text.toString())
            if (script.isEmpty()) {
                status.text = "No valid command was entered."
                return@button
            }
            AutomationEngine(this).execute(script) { message -> status.text = message }
        })

        root.addView(TextView(this).apply { text = "Quick manual controls" })
        val tapX = numberField("X")
        val tapY = numberField("Y")
        root.addView(row(tapX, tapY))
        root.addView(button("Tap") {
            performGesture { service ->
                val x = tapX.intValue()
                val y = tapY.intValue()
                service.tap(x, y) { ok ->
                    status.text = if (ok) "Tap sent at ($x, $y)." else "Gesture could not be dispatched."
                }
            }
        })

        root.addView(button("Open settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })

        root.addView(button("Go home") {
            performGesture { service ->
                val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                status.text = if (ok) "Home pressed." else "Home action unavailable."
            }
        })

        setContentView(root)
    }

    private fun requestScreenCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            status.text = "Notifications permission is required for capture services."
            return
        }
        projectionConsent.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startLiveMonitoring() {
        if (liveMonitoring) return
        liveMonitoring = true
        status.text = "Live monitoring started."
        scheduleMonitorTick()
    }

    private fun stopLiveMonitoring() {
        liveMonitoring = false
        monitorRunnable?.let { monitorHandler.removeCallbacks(it) }
        monitorRunnable = null
        status.text = "Live monitoring stopped."
    }

    private fun scheduleMonitorTick() {
        if (!liveMonitoring) return
        monitorRunnable = Runnable {
            requestScreenCapture()
            monitorHandler.postDelayed(this@MainActivity.monitorRunnable ?: return@Runnable, 2200L)
        }
        monitorHandler.postDelayed(monitorRunnable!!, 2200L)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun numberField(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        setSingleLine(true)
    }

    private fun row(first: EditText, second: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(second, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun EditText.intValue(): Int = text.toString().toIntOrNull() ?: 0

    private fun performGesture(action: (TouchAccessibilityService) -> Unit) {
        val service = TouchAccessibilityService.instance
        if (service == null) {
            status.text = "Enable AI Phone Action under Accessibility settings first."
        } else {
            action(service)
        }
    }

    private fun recognizeText(imageFile: File) {
        val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            status.text = "Could not open captured frame."
            imageFile.delete()
            return
        }

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val text = result.text.ifBlank { "No text detected." }
                val observation = analyzer.analyze(text)
                val decision = decisionEngine.decide(observation.inferredState)
                phaseText.text = "Game state: ${observation.inferredState.name}"
                decisionText.text = "Recommendation: ${decision.primaryAction} | ${decision.summary}"
                status.text = if (text.length > 120) text.take(120) + "..." else text
            }
            .addOnFailureListener { error ->
                status.text = "OCR failed: ${error.localizedMessage}"
            }
            .addOnCompleteListener {
                bitmap.recycle()
                imageFile.delete()
            }
    }

    override fun onDestroy() {
        stopLiveMonitoring()
        unregisterReceiver(captureReceiver)
        super.onDestroy()
    }
}
