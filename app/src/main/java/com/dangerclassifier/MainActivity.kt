package com.dangerclassifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.widget.Button
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var detectionOverlay: DetectionOverlayView
    private lateinit var scoreText: TextView
    private lateinit var scoreLabelText: TextView
    private lateinit var reasoningText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var downloadOverlay: View
    private lateinit var downloadText: TextView
    private lateinit var downloadSubText: TextView
    private lateinit var retryButton: Button

    private val detectorHelper    = ObjectDetectorHelper(this)
    private val classifierHelper  = ImageClassifierHelper(this)
    private var classifierEnabled = false   // graceful fallback if 2nd model fails

    private var lastAnalysisMs  = 0L
    private val analysisInterval = 700L
    private var lastScore        = -1
    private var wasHighDanger    = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        initModels()
    }

    private fun bindViews() {
        previewView      = findViewById(R.id.previewView)
        detectionOverlay = findViewById(R.id.detectionOverlay)
        scoreText        = findViewById(R.id.scoreText)
        scoreLabelText   = findViewById(R.id.scoreLabelText)
        reasoningText    = findViewById(R.id.reasoningText)
        progressBar      = findViewById(R.id.progressBar)
        downloadOverlay  = findViewById(R.id.downloadOverlay)
        downloadText     = findViewById(R.id.downloadStatusText)
        downloadSubText  = findViewById(R.id.downloadSubText)
        retryButton      = findViewById(R.id.retryButton)
        retryButton.setOnClickListener {
            retryButton.visibility = View.GONE
            ModelDownloader.deleteModel(this)
            ModelDownloader.deleteClassifier(this)
            initModels()
        }
    }

    // ── Model lifecycle ──────────────────────────────────────────────────────

    private fun initModels() {
        lifecycleScope.launch {
            // Step 1: detector
            if (!ModelDownloader.isDetectorReady(this@MainActivity)) {
                showDownload("Downloading detection model (1 of 2)…")
                val r = ModelDownloader.download(this@MainActivity) { msg ->
                    runOnUiThread { downloadText.text = msg }
                }
                if (r.isFailure) { showError("Download failed: ${r.exceptionOrNull()?.message}"); return@launch }
            }
            val detOk = detectorHelper.initialize(ModelDownloader.getDetectorFile(this@MainActivity))
            if (!detOk) {
                ModelDownloader.deleteModel(this@MainActivity)
                showError("Detector model failed to load — tap Retry."); return@launch
            }

            // Step 2: classifier (non-blocking — app works without it)
            if (!ModelDownloader.isClassifierReady(this@MainActivity)) {
                showDownload("Downloading scene classifier (2 of 2)…")
                val r = ModelDownloader.downloadClassifier(this@MainActivity) { msg ->
                    runOnUiThread { downloadText.text = msg }
                }
                if (r.isFailure) {
                    Log.w(TAG, "Classifier download failed — continuing detector-only")
                }
            }
            if (ModelDownloader.isClassifierReady(this@MainActivity)) {
                classifierEnabled = classifierHelper.initialize(
                    ModelDownloader.getClassifierFile(this@MainActivity)
                )
            }

            hideDownload()
            checkCameraPermission()
        }
    }

    // ── Camera permission ────────────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), RC_CAMERA)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            startCamera()
        else showError("Camera permission is required to use this app.")
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
                if (now - lastAnalysisMs >= analysisInterval) {
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
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    // ── Inference — detector + classifier run concurrently ───────────────────

    private fun runAnalysis(bitmap: android.graphics.Bitmap, rotation: Int) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {

            // Detector and classifier launched concurrently
            val boxesDeferred  = async { detectorHelper.detect(bitmap, rotation) }
            val labelsDeferred = async {
                if (classifierEnabled) classifierHelper.classify(bitmap, rotation)
                else emptyList()
            }

            val frame  = boxesDeferred.await()
            val rawLabels = labelsDeferred.await()

            // Convert classifier output to SceneLabel with danger scores
            val sceneLabels = rawLabels
                .map { (label, conf) -> SceneLabel(label, conf, DangerScorer.scoreImageNetLabel(label)) }
                .filter { it.dangerScore > 0 && it.confidence > 0.50f }

            val fullFrame = DetectionFrame(frame.boxes, sceneLabels, frame.imageWidth, frame.imageHeight)
            val result    = DangerScorer.analyze(fullFrame.boxes, sceneLabels)

            withContext(Dispatchers.Main) {
                updateUI(result, fullFrame)
                progressBar.visibility = View.GONE
            }
        }
    }

    // ── UI updates ────────────────────────────────────────────────────────────

    private fun updateUI(result: DangerResult, frame: DetectionFrame) {
        val color = when (result.score) {
            in 0..3 -> getColor(R.color.safe_green)
            in 4..6 -> getColor(R.color.warning_yellow)
            in 7..8 -> getColor(R.color.danger_orange)
            else    -> getColor(R.color.extreme_red)
        }
        scoreText.setTextColor(color)
        scoreLabelText.setTextColor(color)
        scoreLabelText.text = result.level
        reasoningText.text  = result.reasoning

        if (result.score != lastScore) {
            lastScore = result.score
            scoreText.text = result.score.toString()
            if (result.score >= 7) {
                scoreText.animate().scaleX(1.14f).scaleY(1.14f).setDuration(110)
                    .withEndAction {
                        scoreText.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    }.start()
            }
        }

        if (result.highlightBoxes.isNotEmpty())
            detectionOverlay.setDetections(result.highlightBoxes, frame.imageWidth, frame.imageHeight)
        else
            detectionOverlay.clear()

        maybeVibrate(result.score)
    }

    // ── Vibration ────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun maybeVibrate(score: Int) {
        val isHigh = score >= 7
        if (isHigh == wasHighDanger) return
        wasHighDanger = isHigh
        if (!isHigh) return

        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (score >= 9)
                VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120, 50, 80), -1)
            else
                VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60), -1)
            vibrator.vibrate(effect)
        } else {
            if (score >= 9) vibrator.vibrate(longArrayOf(0, 80, 50, 120, 50, 80), -1)
            else            vibrator.vibrate(longArrayOf(0, 60, 40, 60), -1)
        }
    }

    // ── Overlay helpers ───────────────────────────────────────────────────────

    private fun showDownload(msg: String) {
        downloadOverlay.visibility = View.VISIBLE
        retryButton.visibility     = View.GONE
        downloadText.text          = msg
        downloadSubText.text       = getString(R.string.one_time_download)
    }

    private fun hideDownload() { downloadOverlay.visibility = View.GONE }

    private fun showError(msg: String) {
        downloadOverlay.visibility = View.VISIBLE
        retryButton.visibility     = View.VISIBLE
        downloadText.text          = "Error"
        downloadSubText.text       = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        detectorHelper.close()
        classifierHelper.close()
    }

    companion object {
        private const val TAG       = "MainActivity"
        private const val RC_CAMERA = 100
    }
}
