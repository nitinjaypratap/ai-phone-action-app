package com.example.aiphoneaction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Single-flight OCR/object pipeline. Drops stale frames and performs bounded retries. */
class FrameProcessor(
    private val objectDetector: VisualObjectDetector,
    private val onResult: (Result) -> Unit
) {
    data class Result(
        val rawText: String,
        val estimate: PhaseEstimate,
        val detections: List<VisualDetection>,
        val ocrError: String? = null,
        val objectError: String? = null,
        val recoveryMessage: String? = null
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val phaseTracker = PhaseConfidenceTracker()
    private val recoveryPolicy = FrameRecoveryPolicy()

    /** Returns false when this frame is stale or a newer frame is already being analyzed. */
    fun submit(file: File): Boolean {
        if (closed.get() || !inFlight.compareAndSet(false, true)) {
            file.delete()
            return false
        }
        return try {
            executor.execute { decodeAndAnalyze(file, retryAttempt = 0) }
            true
        } catch (_: Exception) {
            file.delete()
            inFlight.set(false)
            false
        }
    }

    private fun decodeAndAnalyze(file: File, retryAttempt: Int) {
        if (closed.get()) {
            file.delete()
            finish()
            return
        }
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            retryOrFail(file, null, retryAttempt, "Could not decode captured frame.")
            return
        }
        runOcr(file, bitmap, retryAttempt = 0)
    }

    private fun runOcr(file: File, bitmap: Bitmap, retryAttempt: Int) {
        if (closed.get()) {
            bitmap.recycleSafely()
            file.delete()
            finish()
            return
        }
        try {
            textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    if (closed.get()) {
                        bitmap.recycleSafely()
                        file.delete()
                        finish()
                        return@addOnSuccessListener
                    }
                    val observation = GameScreenAnalyzer().analyze(text.text)
                    val estimate = phaseTracker.update(observation)
                    try {
                        objectDetector.detect(bitmap) { detections, error ->
                            if (!closed.get()) {
                                recoveryPolicy.recordSuccess()
                                onResult(Result(text.text, estimate, detections, objectError = error))
                            }
                            bitmap.recycleSafely()
                            file.delete()
                            finish()
                        }
                    } catch (error: Exception) {
                        recoveryPolicy.recordSuccess() // OCR succeeded; object detection is optional context.
                        if (!closed.get()) onResult(
                            Result(text.text, estimate, emptyList(), objectError = error.localizedMessage ?: "Object detection failed.")
                        )
                        bitmap.recycleSafely()
                        file.delete()
                        finish()
                    }
                }
                .addOnFailureListener { error ->
                    retryOrFail(file, bitmap, retryAttempt, error.localizedMessage ?: "OCR failed.")
                }
        } catch (error: Exception) {
            retryOrFail(file, bitmap, retryAttempt, error.localizedMessage ?: "OCR could not start.")
        }
    }

    private fun retryOrFail(file: File, bitmap: Bitmap?, retryAttempt: Int, reason: String) {
        val delay = recoveryPolicy.retryDelayMs(retryAttempt)
        if (delay != null && !closed.get()) {
            mainHandler.postDelayed({
                if (closed.get()) {
                    bitmap?.recycleSafely()
                    file.delete()
                    finish()
                } else if (bitmap == null) {
                    try {
                        executor.execute { decodeAndAnalyze(file, retryAttempt + 1) }
                    } catch (_: Exception) {
                        failFrame(file, null, "Retry could not be scheduled: $reason")
                    }
                } else {
                    runOcr(file, bitmap, retryAttempt + 1)
                }
            }, delay)
            return
        }
        failFrame(file, bitmap, "$reason Retry limit reached.")
    }

    private fun failFrame(file: File, bitmap: Bitmap?, message: String) {
        bitmap?.recycleSafely()
        file.delete()
        val outcome = recoveryPolicy.recordFailure()
        if (outcome.resetConfidence) phaseTracker.reset()
        val recovery = buildString {
            append("Frame discarded after bounded retry. Consecutive failures: ${outcome.consecutiveFailures}.")
            if (outcome.resetConfidence) append(" Phase confidence reset to UNKNOWN.")
            if (outcome.requiresUserRestart) append(" Repeated failures: stop and restart capture, then grant screen sharing again if needed.")
        }
        mainHandler.post {
            if (!closed.get()) onResult(
                Result(
                    rawText = "",
                    estimate = PhaseEstimate(GameState.UNKNOWN, 0f, 0),
                    detections = emptyList(),
                    ocrError = message,
                    recoveryMessage = recovery
                )
            )
        }
        finish()
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private fun finish() {
        inFlight.set(false)
    }

    fun resetConfidence() {
        phaseTracker.reset()
        recoveryPolicy.reset()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        textRecognizer.close()
        // VisualObjectDetector is owned and closed by the dashboard.
    }

    companion object {
        private const val MAX_RETRIES = 1
    }
}
