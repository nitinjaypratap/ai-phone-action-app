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

/** Single-flight OCR/object pipeline. Drops stale frames and retries transient failures once. */
class FrameProcessor(
    private val objectDetector: VisualObjectDetector,
    private val onResult: (Result) -> Unit
) {
    data class Result(
        val rawText: String,
        val estimate: PhaseEstimate,
        val detections: List<VisualDetection>,
        val ocrError: String? = null,
        val objectError: String? = null
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val phaseTracker = PhaseConfidenceTracker()

    /** Returns false when this frame is stale or a newer frame is already being analyzed. */
    fun submit(file: File): Boolean {
        if (closed.get() || !inFlight.compareAndSet(false, true)) {
            file.delete()
            return false
        }
        try {
            executor.execute { decodeAndAnalyze(file, attempt = 0) }
        } catch (_: Exception) {
            file.delete()
            inFlight.set(false)
            return false
        }
        return true
    }

    private fun decodeAndAnalyze(file: File, attempt: Int) {
        if (closed.get()) {
            file.delete()
            inFlight.set(false)
            return
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            if (attempt < MAX_RETRIES) {
                mainHandler.postDelayed({
                    try {
                        executor.execute { decodeAndAnalyze(file, attempt + 1) }
                    } catch (_: Exception) {
                        file.delete()
                        finish()
                    }
                }, RETRY_DELAY_MS)
            } else {
                file.delete()
                deliverFailure("Could not decode captured frame after retry.")
                finish()
            }
            return
        }
        runOcr(file, bitmap, attempt = 0)
    }

    private fun runOcr(file: File, bitmap: Bitmap, attempt: Int) {
        if (closed.get()) {
            bitmap.recycle()
            file.delete()
            finish()
            return
        }
        textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val rawText = text.text
                val observation = GameScreenAnalyzer().analyze(rawText)
                val estimate = phaseTracker.update(observation)
                objectDetector.detect(bitmap) { detections, error ->
                    if (!closed.get()) {
                        onResult(Result(rawText, estimate, detections, objectError = error))
                    }
                    bitmap.recycle()
                    file.delete()
                    finish()
                }
            }
            .addOnFailureListener { error ->
                if (attempt < MAX_RETRIES && !closed.get()) {
                    mainHandler.postDelayed({ runOcr(file, bitmap, attempt + 1) }, RETRY_DELAY_MS)
                } else {
                    bitmap.recycle()
                    file.delete()
                    deliverFailure(error.localizedMessage ?: "OCR failed after retry.")
                    finish()
                }
            }
    }

    private fun deliverFailure(message: String) {
        mainHandler.post {
            if (!closed.get()) {
                onResult(
                    Result(
                        rawText = "",
                        estimate = PhaseEstimate(GameState.UNKNOWN, 0f, 0),
                        detections = emptyList(),
                        ocrError = message
                    )
                )
            }
        }
    }

    private fun finish() {
        inFlight.set(false)
    }

    fun resetConfidence() {
        phaseTracker.reset()
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.shutdownNow()
        textRecognizer.close()
        objectDetector.close()
    }

    companion object {
        private const val MAX_RETRIES = 1
        private const val RETRY_DELAY_MS = 250L
    }
}
