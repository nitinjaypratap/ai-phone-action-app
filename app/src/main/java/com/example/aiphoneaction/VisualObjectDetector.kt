package com.example.aiphoneaction

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** Generic on-device object proposals only; this does not classify players, enemies, or targets. */
class VisualObjectDetector {
    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    fun detect(bitmap: Bitmap, callback: (List<VisualDetection>, String?) -> Unit) {
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                val detections = objects.map { item ->
                    val box = item.boundingBox
                    val bestLabel = item.labels.maxByOrNull { it.confidence }
                    VisualDetection(
                        label = bestLabel?.text ?: "Object",
                        confidence = bestLabel?.confidence ?: 0f,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom
                    )
                }
                callback(detections, null)
            }
            .addOnFailureListener { error -> callback(emptyList(), error.localizedMessage) }
    }

    fun close() = detector.close()
}
