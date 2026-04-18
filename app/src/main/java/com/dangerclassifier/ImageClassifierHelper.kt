package com.dangerclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

class ImageClassifierHelper(private val context: Context) {

    private var classifier: ImageClassifier? = null

    fun initialize(modelFile: File): Boolean {
        return try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(25)
                .setScoreThreshold(0.04f)
                .build()
            val buffer = FileInputStream(modelFile).channel
                .map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
            classifier = ImageClassifier.createFromBufferAndOptions(buffer, options)
            Log.d(TAG, "ImageClassifier initialized (${modelFile.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init ImageClassifier", e)
            false
        }
    }

    /** Returns list of (label, confidence) sorted by confidence descending. */
    fun classify(bitmap: Bitmap, rotationDegrees: Int): List<Pair<String, Float>> {
        val cls = classifier ?: return emptyList()
        return try {
            val input = if (rotationDegrees != 0) {
                val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            } else bitmap

            cls.classify(TensorImage.fromBitmap(input))
                .flatMap { it.categories.map { c -> Pair(c.label, c.score) } }
                .sortedByDescending { it.second }
        } catch (e: Exception) {
            Log.e(TAG, "Classification failed", e)
            emptyList()
        }
    }

    fun close() {
        classifier?.close()
        classifier = null
    }

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }
}
