package com.dangerclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File

class ObjectDetectorHelper(private val context: Context) {

    private var detector: ObjectDetector? = null

    fun initialize(modelFile: File): Boolean {
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(10)
                .setScoreThreshold(0.3f)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(
                context, modelFile.absolutePath, options
            )
            Log.d(TAG, "ObjectDetector initialized from ${modelFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector", e)
            false
        }
    }

    fun detect(bitmap: Bitmap, rotationDegrees: Int): List<Pair<String, Float>> {
        val det = detector ?: return emptyList()
        return try {
            val rotated = rotateBitmap(bitmap, rotationDegrees)
            val tensorImage = TensorImage.fromBitmap(rotated)
            det.detect(tensorImage).flatMap { detection ->
                detection.categories.map { category ->
                    Pair(category.label, category.score)
                }
            }.sortedByDescending { it.second }
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            emptyList()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun close() {
        detector?.close()
        detector = null
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
    }
}
