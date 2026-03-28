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

    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"
    private const val MODEL_FILENAME = "efficientdet_lite0.tflite"

    fun getModelFile(context: Context): File =
        File(context.filesDir, MODEL_FILENAME)

    fun isModelReady(context: Context): Boolean {
        val f = getModelFile(context)
        return f.exists() && f.length() > 100_000L  // sanity check: model must be >100 KB
    }

    suspend fun download(
        context: Context,
        onProgress: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(context)
        try {
            onProgress("Connecting…")

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val response = client.newCall(Request.Builder().url(MODEL_URL).build()).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Server returned ${response.code}")
                )
            }

            val body = response.body
                ?: return@withContext Result.failure(Exception("Empty response body"))

            val totalBytes = body.contentLength()
            var downloaded = 0L

            FileOutputStream(modelFile).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (totalBytes > 0) {
                            val pct = (downloaded * 100 / totalBytes).toInt()
                            val mb = String.format("%.1f", downloaded / 1_048_576f)
                            onProgress("Downloading model… $pct% ($mb MB)")
                        }
                    }
                }
            }

            Result.success(modelFile)
        } catch (e: Exception) {
            modelFile.delete()
            Result.failure(e)
        }
    }
}
