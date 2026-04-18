package com.dangerclassifier

import android.graphics.RectF

data class DetectionBox(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val dangerScore: Int
)

/** Scene-level label from the ImageNet classifier (no bounding box). */
data class SceneLabel(
    val label: String,
    val confidence: Float,
    val dangerScore: Int
)

data class DetectionFrame(
    val boxes: List<DetectionBox>,
    val sceneLabels: List<SceneLabel>,   // from EfficientNet classifier
    val imageWidth: Int,
    val imageHeight: Int
)
