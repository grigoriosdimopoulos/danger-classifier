package com.dangerclassifier

import android.graphics.RectF

data class DetectionBox(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val dangerScore: Int
)

data class DetectionFrame(
    val boxes: List<DetectionBox>,
    val imageWidth: Int,
    val imageHeight: Int
)
