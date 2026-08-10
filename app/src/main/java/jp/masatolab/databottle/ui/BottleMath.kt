package jp.masatolab.databottle.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object BottleMath {
    fun isInside(x: Float, y: Float): Boolean {
        if (y < 0.035f || y > 0.965f) return false
        val dx = abs(x - 0.5f)
        return dx <= halfWidth(y)
    }

    fun isOutline(x: Float, y: Float, step: Float = 0.012f): Boolean {
        if (!isInside(x, y)) return false
        return !isInside(x - step, y) ||
            !isInside(x + step, y) ||
            !isInside(x, y - step) ||
            !isInside(x, y + step)
    }

    private fun halfWidth(y: Float): Float = when {
        y < 0.065f -> 0.145f // lip
        y < 0.175f -> 0.118f // neck
        y < 0.29f -> {
            val t = smoothStep((y - 0.175f) / (0.29f - 0.175f))
            lerp(0.118f, 0.365f, t)
        }
        y < 0.865f -> 0.365f
        else -> {
            val t = smoothStep((y - 0.865f) / (0.965f - 0.865f))
            lerp(0.365f, 0.315f, t)
        }
    }

    /**
     * 0 at the middle of the vessel and approaches 1 very close to a side wall.
     * A high-order curve keeps the capillary rise confined to the last few dots.
     */
    fun sideWallProximity(x: Float, y: Float): Float {
        if (!isInside(x, y)) return 0f
        val width = halfWidth(y).coerceAtLeast(0.001f)
        val q = (abs(x - 0.5f) / width).coerceIn(0f, 1f)
        val q2 = q * q
        val q4 = q2 * q2
        return q4 * q4 // q^8
    }

    /**
     * Score used to decide which interior dots are submerged.
     *
     * The base term is the gravity projection, so the mean free surface remains
     * perpendicular to gravity. While moving, a broad primary wave is blended
     * with a weaker higher-frequency secondary wave. v0.1.6 gives the secondary
     * wave an independent phase and slightly varies its blend on each new slosh,
     * so repeated phone motions do not produce an obvious loop.
     *
     * meniscusAmplitude is deliberately supplied by the caller. DATA BOTTLE
     * passes zero at rest and a very small value only while moving.
     */
    fun surfaceScore(
        x: Float,
        y: Float,
        downX: Float,
        downY: Float,
        phase: Float,
        secondaryPhase: Float = 0f,
        secondaryMix: Float = 0.30f,
        waveAmplitude: Float,
        meniscusAmplitude: Float = 0f
    ): Float {
        val p = projection(x, y, downX, downY)

        // Tangent to the mean liquid surface.
        val tangentX = -downY
        val tangentY = downX
        val t = (x - 0.5f) * tangentX + (y - 0.5f) * tangentY

        // Broad slosh + a weaker independent ripple. Keeping the blend around
        // 70/30 preserves a readable free surface while avoiding a repeated sine.
        val primaryAngle = t * ((2f * PI.toFloat() * 1.2f) / 0.73f) + phase
        val secondaryAngle =
            t * ((2f * PI.toFloat() * 2.1f) / 0.73f) +
                phase * 0.63f + secondaryPhase

        val secondaryWeight = secondaryMix.coerceIn(0.20f, 0.40f)
        val primaryWeight = 1f - secondaryWeight
        val primary = sin(primaryAngle) * primaryWeight
        val secondary = sin(secondaryAngle) * secondaryWeight
        val waveOffset = waveAmplitude * (primary + secondary)

        // Positive score means the near-wall liquid fills slightly earlier.
        val meniscus = meniscusAmplitude * sideWallProximity(x, y)

        return p - waveOffset + meniscus
    }

    fun fillThreshold(
        columns: Int,
        rows: Int,
        downX: Float,
        downY: Float,
        fraction: Float
    ): Float {
        val f = fraction.coerceIn(0f, 1f)
        var minProjection = Float.POSITIVE_INFINITY
        var maxProjection = Float.NEGATIVE_INFINITY
        var insideCount = 0

        for (row in 0 until rows) {
            val y = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val x = (column + 0.5f) / columns
                if (!isInside(x, y) || isOutline(x, y)) continue
                val p = projection(x, y, downX, downY)
                minProjection = min(minProjection, p)
                maxProjection = max(maxProjection, p)
                insideCount++
            }
        }

        if (insideCount == 0) return 0f
        if (f <= 0f) return maxProjection + 1f
        if (f >= 1f) return minProjection - 1f

        var low = minProjection
        var high = maxProjection
        repeat(14) {
            val mid = (low + high) * 0.5f
            var filled = 0
            for (row in 0 until rows) {
                val y = (row + 0.5f) / rows
                for (column in 0 until columns) {
                    val x = (column + 0.5f) / columns
                    if (!isInside(x, y) || isOutline(x, y)) continue
                    if (projection(x, y, downX, downY) >= mid) filled++
                }
            }
            val actual = filled.toFloat() / insideCount.toFloat()
            if (actual > f) low = mid else high = mid
        }
        return (low + high) * 0.5f
    }

    /**
     * Finds a fill threshold from a precomputed score grid. NaN entries are
     * ignored. This keeps the exact liquid fraction while avoiding thousands of
     * sin() calls inside the binary-search loop on every animation frame.
     */
    fun fillThresholdFromScores(
        scores: FloatArray,
        insideCount: Int,
        minScore: Float,
        maxScore: Float,
        fraction: Float
    ): Float {
        if (insideCount <= 0) return 0f
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) return maxScore + 1f
        if (f >= 1f) return minScore - 1f

        var low = minScore
        var high = maxScore
        repeat(14) {
            val mid = (low + high) * 0.5f
            var filled = 0
            for (score in scores) {
                if (!score.isNaN() && score >= mid) filled++
            }
            val actual = filled.toFloat() / insideCount.toFloat()
            if (actual > f) low = mid else high = mid
        }
        return (low + high) * 0.5f
    }


    /**
     * Rounds a requested static fill fraction to the nearest complete horizontal
     * dot-row boundary. This intentionally trades a tiny amount of percentage
     * precision for a perfectly flat resting surface.
     *
     * When [fillFromBottom] is true the liquid accumulates from the bottom of
     * the bottle; when false it accumulates from the top (for upside-down use).
     */
    fun snapFractionToHorizontalRows(
        columns: Int,
        rows: Int,
        fraction: Float,
        fillFromBottom: Boolean = true
    ): Float {
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f || f >= 1f) return f

        val counts = IntArray(rows)
        var insideCount = 0
        for (row in 0 until rows) {
            val y = (row + 0.5f) / rows
            var rowCount = 0
            for (column in 0 until columns) {
                val x = (column + 0.5f) / columns
                if (isInside(x, y) && !isOutline(x, y)) rowCount++
            }
            counts[row] = rowCount
            insideCount += rowCount
        }

        if (insideCount <= 0) return f

        val targetDots = f * insideCount.toFloat()
        var cumulative = 0
        var bestDots = 0
        var bestError = kotlin.math.abs(targetDots)

        val range: IntProgression = if (fillFromBottom) {
            (rows - 1) downTo 0
        } else {
            0 until rows
        }

        for (row in range) {
            cumulative += counts[row]
            val error = kotlin.math.abs(cumulative.toFloat() - targetDots)
            if (error < bestError) {
                bestError = error
                bestDots = cumulative
            }
        }

        return bestDots.toFloat() / insideCount.toFloat()
    }

    fun projection(x: Float, y: Float, downX: Float, downY: Float): Float =
        (x - 0.5f) * downX + (y - 0.5f) * downY

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
