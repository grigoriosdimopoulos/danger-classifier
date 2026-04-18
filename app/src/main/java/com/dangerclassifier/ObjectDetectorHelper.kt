package com.dangerclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ObjectDetectorHelper(private val context: Context) {

    private var detector: ObjectDetector? = null

    fun initialize(modelFile: File): Boolean {
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(15)
                .setScoreThreshold(0.3f)
                .build()
            val mappedBuffer = mapModelFile(modelFile)
            detector = ObjectDetector.createFromBufferAndOptions(mappedBuffer, options)
            Log.d(TAG, "ObjectDetector initialized (${modelFile.length() / 1024} KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectDetector", e)
            false
        }
    }

    private fun mapModelFile(file: File): MappedByteBuffer {
        val fis = FileInputStream(file)
        return fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, fis.channel.size())
    }

    fun detect(bitmap: Bitmap, rotationDegrees: Int): DetectionFrame {
        val det = detector ?: return DetectionFrame(emptyList(), emptyList(), bitmap.width, bitmap.height)
        return try {
            val rotated = rotateBitmap(bitmap, rotationDegrees)
            val tensorImage = TensorImage.fromBitmap(rotated)
            val results = det.detect(tensorImage)

            val boxes = results.flatMap { detection ->
                detection.categories.take(1).map { category ->
                    DetectionBox(
                        label       = category.label,
                        confidence  = category.score,
                        boundingBox = detection.boundingBox,
                        dangerScore = DangerScorer.getDangerScore(category.label)
                    )
                }
            }.sortedByDescending { it.dangerScore * it.confidence }

            DetectionFrame(boxes, emptyList(), rotated.width, rotated.height)
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            DetectionFrame(emptyList(), emptyList(), bitmap.width, bitmap.height)
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
