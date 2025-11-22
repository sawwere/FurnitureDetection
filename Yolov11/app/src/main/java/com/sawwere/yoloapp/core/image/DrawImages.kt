package com.sawwere.yoloapp.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import com.sawwere.yoloapp.R
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.detection.SegmentationResult

class DrawImages(private val context: Context) {

    private val boxColors = listOf(
        R.color.overlay_orange,
        R.color.overlay_blue,
        R.color.overlay_green,
        R.color.overlay_red,
        R.color.overlay_pink,
        R.color.overlay_cyan,
        R.color.overlay_purple,
        R.color.overlay_gray,
        R.color.overlay_teal,
        R.color.overlay_yellow,
    )

    private val boxPaint = Paint().apply {
        color = Color.valueOf(1.0f, 0f, 0f).toArgb()
        strokeWidth = 2F
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        style = Paint.Style.FILL
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }


    operator fun invoke(
        imageWidth: Int,
        imageHeight: Int,
        results: List<DetectionComponent.Detection>
    ) : Bitmap {
        val combined = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)

        results.forEach { detection ->
            combined.applyCanvas {
                drawRect(detection.bbox, boxPaint)
//                drawText(detection.confidence.toString(), detection.bbox.left, detection.bbox.top, labelPaint)
                val label = "${detection.className} ${(detection.confidence * 100).toInt()}%"
                drawText(label, detection.bbox.left, detection.bbox.top - 10f, labelPaint)
            }
        }
//        results.forEach { result ->
//            val colorResId = boxColors[result.box.cls % 10]
//            applyTransparentOverlay(context, combined, result, colorResId)
//        }
        return combined
    }

    private fun applyTransparentOverlay(context: Context, overlay: Bitmap, segmentationResult: SegmentationResult, overlayColorResId: Int) {
        val width = overlay.width
        val height = overlay.height

        val overlayColor = ContextCompat.getColor(context, overlayColorResId)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val maskValue = segmentationResult.mask[y][x]
                if (maskValue > 0) {
                    overlay.setPixel(x, y, applyTransparentOverlayColor(overlayColor))
                }
            }
        }

        val canvas = Canvas(overlay)

        val boxPaint = Paint().apply {
            color = ContextCompat.getColor(context, overlayColorResId)
            strokeWidth = 2F
            style = Paint.Style.STROKE
        }

        val box = segmentationResult.box

        val left = (box.x1 * width).toInt()
        val top = (box.y1 * height).toInt()
        val right = (box.x2 * width).toInt()
        val bottom = (box.y2 * height).toInt()

        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), boxPaint)

        val textBackgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, overlayColorResId)
            style = Paint.Style.FILL
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            textSize = 16f
        }

        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(box.clsName, 0, box.clsName.length, bounds)

        val textWidth = bounds.width()
        val textHeight = bounds.height()
        val padding = 2

        canvas.drawRect(
            left.toFloat(),
            top.toFloat() - textHeight - 2 * padding,
            left + textWidth + 2 * padding.toFloat(),
            top.toFloat(),
            textBackgroundPaint
        )
        canvas.drawText(box.clsName, left.toFloat() + padding, top.toFloat() - padding.toFloat(), textPaint)
    }

    private fun applyTransparentOverlayColor(color: Int): Int {
        val alpha = 48
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(alpha, red, green, blue)
    }
}