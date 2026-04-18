package com.dangerclassifier

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class DetectionOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class ScreenBox(
        val rect: RectF,
        val label: String,
        val confidence: Float,
        val dangerScore: Int
    )

    private var screenBoxes: List<ScreenBox> = emptyList()

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(100, 0, 0, 0)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val chipFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setDetections(boxes: List<DetectionBox>, imgWidth: Int, imgHeight: Int) {
        if (width == 0 || height == 0 || imgWidth == 0 || imgHeight == 0) {
            post { setDetections(boxes, imgWidth, imgHeight) }
            return
        }
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = maxOf(vw / imgWidth, vh / imgHeight)
        val ox = (vw - imgWidth * scale) / 2f
        val oy = (vh - imgHeight * scale) / 2f

        screenBoxes = boxes.map { b ->
            ScreenBox(
                rect = RectF(
                    b.boundingBox.left  * scale + ox,
                    b.boundingBox.top   * scale + oy,
                    b.boundingBox.right * scale + ox,
                    b.boundingBox.bottom* scale + oy
                ),
                label       = b.label,
                confidence  = b.confidence,
                dangerScore = b.dangerScore
            )
        }
        invalidate()
    }

    fun clear() {
        screenBoxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        for (box in screenBoxes) drawBox(canvas, box)
    }

    private fun drawBox(canvas: Canvas, box: ScreenBox) {
        val color  = dangerColor(box.dangerScore)
        val r      = box.rect
        val corner = minOf(r.width(), r.height()) * 0.22f
        val stroke = dp(3f)

        // Shadow (slightly wider, black) for contrast over any background
        shadowPaint.strokeWidth = stroke + dp(3f)
        drawBrackets(canvas, r, corner, shadowPaint)

        // Colored bracket
        bracketPaint.color = color
        bracketPaint.strokeWidth = stroke
        drawBrackets(canvas, r, corner, bracketPaint)

        // Label chip positioned at top-left of box
        val chipPad  = dp(7f)
        val chipH    = dp(24f)
        labelPaint.textSize = dp(11f)
        val text     = "${box.label.uppercase()}  ${(box.confidence * 100).toInt()}%"
        val textW    = labelPaint.measureText(text)
        val chipRect = RectF(r.left, r.top - chipH - dp(2f), r.left + textW + chipPad * 2, r.top - dp(2f))

        // Shadow under chip
        chipFillPaint.color = Color.argb(160, 0, 0, 0)
        canvas.drawRoundRect(chipRect.also { it.offset(dp(1f), dp(1f)) }, dp(4f), dp(4f), chipFillPaint)

        chipFillPaint.color = color
        canvas.drawRoundRect(chipRect, dp(4f), dp(4f), chipFillPaint)
        canvas.drawText(text, chipRect.left + chipPad, chipRect.bottom - dp(6f), labelPaint)
    }

    private fun drawBrackets(canvas: Canvas, r: RectF, len: Float, p: Paint) {
        // Top-left
        canvas.drawLine(r.left, r.top + len, r.left, r.top, p)
        canvas.drawLine(r.left, r.top, r.left + len, r.top, p)
        // Top-right
        canvas.drawLine(r.right - len, r.top, r.right, r.top, p)
        canvas.drawLine(r.right, r.top, r.right, r.top + len, p)
        // Bottom-left
        canvas.drawLine(r.left, r.bottom - len, r.left, r.bottom, p)
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, p)
        // Bottom-right
        canvas.drawLine(r.right - len, r.bottom, r.right, r.bottom, p)
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, p)
    }

    private fun dangerColor(score: Int) = when (score) {
        in 0..3 -> 0xFF4CAF50.toInt()
        in 4..6 -> 0xFFFF9800.toInt()
        in 7..8 -> 0xFFFF4500.toInt()
        else    -> 0xFFE53935.toInt()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
