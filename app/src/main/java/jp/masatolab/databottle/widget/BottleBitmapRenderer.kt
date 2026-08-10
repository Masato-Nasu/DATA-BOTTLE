package jp.masatolab.databottle.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import jp.masatolab.databottle.data.BottleType
import jp.masatolab.databottle.data.MetricResult
import jp.masatolab.databottle.ui.BottleMath
import kotlin.math.floor
import kotlin.math.min

object BottleBitmapRenderer {
    private const val PRIMARY = 0xFFB8FFF1.toInt()
    private const val OVERFLOW = 0xFFFF6B6B.toInt()
    private const val MUTED = 0xFFB2C0BC.toInt()
    private const val BACKGROUND = 0xFF000000.toInt()

    fun render(metric: MetricResult, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = PRIMARY
        paint.textSize = width * 0.07f
        canvas.drawText(metric.type.label, width / 2f, height * 0.10f, paint)

        val bottleLeft = width * 0.18f
        val bottleTop = height * 0.15f
        val bottleWidth = width * 0.64f
        val bottleHeight = height * 0.60f
        drawBottle(canvas, metric.type, metric.ratio, bottleLeft, bottleTop, bottleWidth, bottleHeight)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.WHITE
        paint.textSize = width * 0.115f
        canvas.drawText(metric.headline, width / 2f, height * 0.86f, paint)

        paint.color = if (metric.needsUsageAccess) OVERFLOW else MUTED
        paint.textSize = width * 0.043f
        canvas.drawText(metric.detail, width / 2f, height * 0.93f, paint)

        return bitmap
    }

    private fun drawBottle(
        canvas: Canvas,
        type: BottleType,
        ratio: Float,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val columns = 48
        val rows = 96
        val stepX = width / columns
        val stepY = height / rows
        val radius = min(stepX, stepY) * 0.205f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun dot(column: Int, row: Int, color: Int, alpha: Int = 255) {
            paint.color = color
            paint.alpha = alpha
            canvas.drawCircle(
                left + (column + 0.5f) * stepX,
                top + (row + 0.5f) * stepY,
                radius,
                paint
            )
        }

        for (row in 0 until rows) {
            val y = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val x = (column + 0.5f) / columns
                if (!BottleMath.isInside(x, y)) continue
                if (BottleMath.isOutline(x, y)) dot(column, row, PRIMARY, 71)
                else dot(column, row, PRIMARY, 31)
            }
        }

        val scores = FloatArray(columns * rows) { Float.NaN }
        var insideCount = 0
        var minScore = Float.POSITIVE_INFINITY
        var maxScore = Float.NEGATIVE_INFINITY
        for (row in 0 until rows) {
            val y = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val x = (column + 0.5f) / columns
                if (!BottleMath.isInside(x, y) || BottleMath.isOutline(x, y)) continue
                val score = BottleMath.surfaceScore(
                    x = x,
                    y = y,
                    downX = 0f,
                    downY = 1f,
                    phase = 0f,
                    waveAmplitude = 0f,
                    meniscusAmplitude = 0f
                )
                scores[row * columns + column] = score
                if (score < minScore) minScore = score
                if (score > maxScore) maxScore = score
                insideCount++
            }
        }

        fun drawFill(fraction: Float, color: Int) {
            if (fraction <= 0f || insideCount <= 0) return
            val threshold = BottleMath.fillThresholdFromScores(
                scores = scores,
                insideCount = insideCount,
                minScore = minScore,
                maxScore = maxScore,
                fraction = BottleMath.snapFractionToHorizontalRows(
                    columns = columns,
                    rows = rows,
                    fraction = fraction,
                    fillFromBottom = true
                )
            )
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val score = scores[row * columns + column]
                    if (!score.isNaN() && score >= threshold) {
                        dot(column, row, color)
                    }
                }
            }
        }

        if (type.supportsOverflowLayers && ratio >= 1f) {
            val completed = floor(ratio).toInt().coerceAtLeast(1)
            val nextFraction = (ratio - completed).coerceIn(0f, 1f)
            val baseColor = if (completed % 2 == 1) PRIMARY else OVERFLOW
            val nextColor = if (baseColor == PRIMARY) OVERFLOW else PRIMARY
            drawFill(1f, baseColor)
            drawFill(nextFraction, nextColor)
        } else {
            drawFill(ratio.coerceIn(0f, 1f), PRIMARY)
        }

        for (row in 0 until rows) {
            val y = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val x = (column + 0.5f) / columns
                if (BottleMath.isOutline(x, y)) dot(column, row, PRIMARY, 71)
            }
        }
    }
}
