package com.example.aiphoneaction

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

/** Generic on-device object proposals only; this does not classify players, enemies, or targets. */
class VisualObjectDetector {
    private val closed = AtomicBoolean(false)
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    fun detect(bitmap: Bitmap, callback: (List<VisualDetection>, String?) -> Unit) {
        if (closed.get()) {
            callback(emptyList(), "Object detector is closed.")
            return
        }
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                val detections = objects.map { item ->
                    val box = item.boundingBox
                    val bestLabel = item.labels.maxByOrNull { it.confidence }
                    VisualDetection(bestLabel?.text ?: "Object", bestLabel?.confidence ?: 0f, box.left, box.top, box.right, box.bottom)
                }
                callback(detections, null)
            }
            .addOnFailureListener { error -> callback(emptyList(), error.localizedMessage ?: "Object detection failed.") }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) detector.close()
    }
}
