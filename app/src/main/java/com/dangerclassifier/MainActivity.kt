package com.dangerclassifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scoreText: TextView
    private lateinit var scoreLabelText: TextView
    private lateinit var reasoningText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadOverlay: View
    private lateinit var downloadText: TextView
    private lateinit var downloadSubText: TextView

    private val detectorHelper = ObjectDetectorHelper(this@MainActivity)
    private var lastAnalysisMs = 0L
    private val analysisIntervalMs = 700L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        initModel()
    }

    private fun bindViews() {
        previewView     = findViewById(R.id.previewView)
        scoreText       = findViewById(R.id.scoreText)
        scoreLabelText  = findViewById(R.id.scoreLabelText)
        reasoningText   = findViewById(R.id.reasoningText)
        progressBar     = findViewById(R.id.progressBar)
        downloadOverlay = findViewById(R.id.downloadOverlay)
        downloadText    = findViewById(R.id.downloadStatusText)
        downloadSubText = findViewById(R.id.downloadSubText)
    }

    // ── Model lifecycle ──────────────────────────────────────────────────────

    private fun initModel() {
        if (ModelDownloader.isModelReady(this)) {
            loadAndStart()
        } else {
            showDownload("Preparing to download detection model…")
            lifecycleScope.launch {
                val result = ModelDownloader.download(this@MainActivity) { msg ->
                    runOnUiThread { downloadText.text = msg }
                }
                result.fold(
                    onSuccess = { loadAndStart() },
                    onFailure = { e -> showError("Download failed: ${e.message}") }
                )
            }
        }
    }

    private fun loadAndStart() {
        val ok = detectorHelper.initialize(ModelDownloader.getModelFile(this))
        if (!ok) {
            showError("Failed to load detection model. Delete app data and retry.")
            return
        }
        hideDownload()
        checkCameraPermission()
    }

    // ── Camera permission ────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), RC_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            showError("Camera permission is required to use this app.")
        }
    }

    // ── CameraX ──────────────────────────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastAnalysisMs >= analysisIntervalMs) {
                    lastAnalysisMs = now
                    val bitmap   = imageProxy.toBitmap()
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    imageProxy.close()
                    runAnalysis(bitmap, rotation)
                } else {
                    imageProxy.close()
                }
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Inference ────────────────────────────────────────────────────────────

    private fun runAnalysis(bitmap: android.graphics.Bitmap, rotation: Int) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val detections = detectorHelper.detect(bitmap, rotation)
            val result     = DangerScorer.analyze(detections)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateUI(result)
                progressBar.visibility = View.GONE
            }
        }
    }

    // ── UI updates ───────────────────────────────────────────────────────────

    private fun updateUI(result: DangerResult) {
        scoreText.text      = result.score.toString()
        scoreLabelText.text = result.level
        reasoningText.text  = result.reasoning

        val color = when (result.score) {
            in 0..3 -> getColor(R.color.safe_green)
            in 4..6 -> getColor(R.color.warning_yellow)
            in 7..8 -> getColor(R.color.danger_orange)
            else    -> getColor(R.color.extreme_red)
        }
        scoreText.setTextColor(color)
        scoreLabelText.setTextColor(color)
    }

    private fun showDownload(msg: String) {
        downloadOverlay.visibility = View.VISIBLE
        downloadText.text          = msg
        downloadSubText.text       = getString(R.string.one_time_download)
    }

    private fun hideDownload() {
        downloadOverlay.visibility = View.GONE
    }

    private fun showError(msg: String) {
        downloadOverlay.visibility = View.VISIBLE
        downloadText.text          = "Error"
        downloadSubText.text       = msg
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        detectorHelper.close()
    }

    companion object {
        private const val TAG      = "MainActivity"
        private const val RC_CAMERA = 100
    }
}
