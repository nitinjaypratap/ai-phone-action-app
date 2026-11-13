package com.example.aiphoneaction

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
    private lateinit var commandInput: EditText
    private val gameScreenAnalyzer = GameScreenAnalyzer()

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
            status.text = "Capturing one frame..."
        } else {
            status.text = "Screen capture was not approved."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receiverFilter = IntentFilter(ScreenCaptureService.ACTION_CAPTURED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, receiverFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, receiverFilter)
        }
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }

        root.addView(TextView(this).apply {
            text = "AI Phone Action — prototype"
            textSize = 22f
        })

        root.addView(TextView(this).apply {
            text = "Foundation automation, OCR, and explicit gesture controls."
            textSize = 14f
            setPadding(0, 10, 0, 12)
        })

        status = TextView(this).apply {
            text = "Ready"
            textSize = 14f
        }
        root.addView(status)

        root.addView(button("Enable touch actions") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(button("Capture screen + read text") {
            startScreenCapture()
        })

        root.addView(button("Open settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })

        root.addView(button("Go home") {
            performGesture { service ->
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                status.text = "Home pressed."
            }
        })

        root.addView(TextView(this).apply {
            text = "Automation command"
            setPadding(0, 16, 0, 0)
        })

        commandInput = EditText(this).apply {
            setText("tap 100 200; wait 500; swipe 100 200 800 1200")
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

        root.addView(TextView(this).apply { text = "Tap coordinates (screen pixels)" })
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

        root.addView(TextView(this).apply { text = "Swipe start and end coordinates" })
        val x1 = numberField("Start X")
        val y1 = numberField("Start Y")
        val x2 = numberField("End X")
        val y2 = numberField("End Y")
        root.addView(row(x1, y1))
        root.addView(row(x2, y2))
        root.addView(button("Swipe") {
            performGesture { service ->
                service.swipe(x1.intValue(), y1.intValue(), x2.intValue(), y2.intValue()) { ok ->
                    status.text = if (ok) "Swipe sent." else "Gesture could not be dispatched."
                }
            }
        })

        setContentView(root)
    }

    private fun startScreenCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            status.text = "Notifications permission is required to show the capture foreground service."
            return
        }
        projectionConsent.launch(projectionManager.createScreenCaptureIntent())
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
                val extractedText = result.text.ifBlank { "No text detected." }
                val observation = gameScreenAnalyzer.analyze(extractedText)
                status.text = "OCR: ${extractedText.take(1000)}\nGame state: ${observation.inferredState.name}"
            }
            .addOnFailureListener { error -> status.text = "OCR failed: ${error.localizedMessage}" }
            .addOnCompleteListener {
                bitmap.recycle()
                imageFile.delete()
            }
    }

    override fun onDestroy() {
        unregisterReceiver(captureReceiver)
        super.onDestroy()
    }
}
