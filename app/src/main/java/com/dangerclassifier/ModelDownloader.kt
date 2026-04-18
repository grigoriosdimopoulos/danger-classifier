package com.dangerclassifier

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ModelDownloader {

    private data class ModelSpec(val url: String, val filename: String, val minBytes: Long)

    // EfficientDet Lite2 — COCO 90, mAP 29.4, confirmed available
    private val DETECTOR = ModelSpec(
        url      = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/object_detection/android/lite-model_efficientdet_lite2_detection_metadata_1.tflite",
        filename = "efficientdet_lite2_detection.tflite",
        minBytes = 1_000_000L
    )

    // EfficientNet Lite2 — ImageNet 1000 classes, top-1 ~77.5% (vs Lite0 ~75.1%), same URL pattern
    private val CLASSIFIER = ModelSpec(
        url      = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/image_classification/android/lite-model_efficientnet_lite2_uint8_2.tflite",
        filename = "efficientnet_lite2_classifier.tflite",
        minBytes = 1_000_000L
    )

    fun getDetectorFile(context: Context): File    = File(context.filesDir, DETECTOR.filename)
    fun getClassifierFile(context: Context): File  = File(context.filesDir, CLASSIFIER.filename)

    // Legacy name kept so existing call-sites compile unchanged
    fun getModelFile(context: Context): File = getDetectorFile(context)

    fun isDetectorReady(context: Context): Boolean    = ready(getDetectorFile(context),    DETECTOR.minBytes)
    fun isClassifierReady(context: Context): Boolean  = ready(getClassifierFile(context),  CLASSIFIER.minBytes)

    // Legacy — still used by loadAndStart() check
    fun isModelReady(context: Context): Boolean = isDetectorReady(context)

    fun deleteModel(context: Context)      { getDetectorFile(context).delete() }
    fun deleteClassifier(context: Context) { getClassifierFile(context).delete() }

    private fun ready(f: File, min: Long) = f.exists() && f.length() > min

    // --- Generic download -------------------------------------------------

    suspend fun download(
        context: Context,
        onProgress: (String) -> Unit
    ): Result<File> = downloadSpec(context, DETECTOR, "model 1 of 2", onProgress)

    suspend fun downloadClassifier(
        context: Context,
        onProgress: (String) -> Unit
    ): Result<File> = downloadSpec(context, CLASSIFIER, "model 2 of 2", onProgress)

    private suspend fun downloadSpec(
        context: Context,
        spec: ModelSpec,
        label: String,
        onProgress: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, spec.filename)
        try {
            onProgress("Connecting ($label)…")
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()

            val response = client.newCall(Request.Builder().url(spec.url).build()).execute()
            if (!response.isSuccessful)
                return@withContext Result.failure(Exception("Server ${response.code}"))

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty body"))

            val total = body.contentLength()
            var done  = 0L

            FileOutputStream(file).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16_384)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            val mb  = "%.1f".format(done / 1_048_576f)
                            onProgress("Downloading $label… $pct% (${mb} MB)")
                        }
                    }
                }
            }
            Result.success(file)
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }
}
