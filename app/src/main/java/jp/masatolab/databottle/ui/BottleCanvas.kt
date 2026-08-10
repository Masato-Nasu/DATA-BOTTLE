package jp.masatolab.databottle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import jp.masatolab.databottle.data.BottleType
import jp.masatolab.databottle.sensor.GravityVector
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private const val DOT_COLUMNS = 56
private const val DOT_ROWS = 112
private const val MOVING_MENISCUS_DOTS = 0.8f

private data class FluidSurfaceState(
    val downX: Float,
    val downY: Float,
    val waveAmplitudeDots: Float,
    val phase: Float,
    val secondaryPhase: Float,
    val secondaryMix: Float,
    val moving: Boolean
)

@Composable
private fun rememberFluidSurface(gravity: GravityVector): FluidSurfaceState {
    val (targetDownX, targetDownY) = gravity.canvasDown()
    val latestTargetX by rememberUpdatedState(targetDownX)
    val latestTargetY by rememberUpdatedState(targetDownY)

    val simulator = remember { BottleSurfaceSimulator() }
    var surface by remember {
        mutableStateOf(
            FluidSurfaceState(
                downX = targetDownX,
                downY = targetDownY,
                waveAmplitudeDots = 0f,
                phase = 0f,
                secondaryPhase = 0f,
                secondaryMix = 0.30f,
                moving = false
            )
        )
    }

    /*
     * v0.1.7: pixel-flat at rest, organic in motion.
     *
     * The liquid surface is perfectly straight once the phone is still. Each
     * newly-started slosh gets subtly different primary/secondary phases, mix,
     * and travel speed. The variation is intentionally small enough to feel
     * physical rather than random.
     */
    LaunchedEffect(Unit) {
        simulator.reset(targetDownX, targetDownY)
        var lastFrameNanos = 0L

        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }

                val dtSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                    .coerceIn(0f, 0.033f)
                lastFrameNanos = frameNanos

                simulator.setTarget(latestTargetX, latestTargetY)
                val frame = simulator.update(dtSeconds)

                surface = FluidSurfaceState(
                    downX = frame.downX,
                    downY = frame.downY,
                    waveAmplitudeDots = frame.waveAmplitudeDots,
                    phase = frame.phase,
                    secondaryPhase = frame.secondaryPhase,
                    secondaryMix = frame.secondaryMix,
                    moving = frame.moving
                )
            }
        }
    }

    return surface
}

@Composable
fun DotBottle(
    type: BottleType,
    ratio: Float,
    gravity: GravityVector,
    modifier: Modifier = Modifier
) {
    val surface = rememberFluidSurface(gravity)
    val scoreGrid = remember { FloatArray(DOT_COLUMNS * DOT_ROWS) }

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.74f)
            .aspectRatio(0.56f)
    ) {
        val columns = DOT_COLUMNS
        val rows = DOT_ROWS
        val stepX = size.width / columns
        val stepY = size.height / rows
        val radius = min(stepX, stepY) * 0.205f

        val fluidLength = sqrt(surface.downX * surface.downX + surface.downY * surface.downY)
        val downX = if (fluidLength > 0.001f) surface.downX / fluidLength else 0f
        val downY = if (fluidLength > 0.001f) surface.downY / fluidLength else 1f

        // Convert the requested dot amplitude into the same normalized space
        // BottleMath uses. This keeps the visual strength stable as density changes.
        val waveAmplitude = surface.waveAmplitudeDots / rows.toFloat()
        val meniscusAmplitude = if (surface.moving) {
            MOVING_MENISCUS_DOTS / rows.toFloat()
        } else {
            0f
        }

        // v0.1.7: when the liquid is fully settled and the device is effectively
        // on a cardinal axis, trade a tiny amount of fill precision for a truly
        // flat dot-row surface. The numeric label still shows the exact metric.
        val snapStaticFillToRows = !surface.moving && abs(downX) < 0.0001f

        fun visualFraction(fraction: Float): Float {
            val f = fraction.coerceIn(0f, 1f)
            return if (snapStaticFillToRows) {
                BottleMath.snapFractionToHorizontalRows(
                    columns = columns,
                    rows = rows,
                    fraction = f,
                    fillFromBottom = downY >= 0f
                )
            } else {
                f
            }
        }

        // Liquid is the brightest thing on screen. The vessel only hints at its presence.
        val outlineColor = BottlePrimary.copy(alpha = 0.28f)
        val ghostColor = BottlePrimary.copy(alpha = 0.12f)

        fun dot(column: Int, row: Int, color: Color) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset((column + 0.5f) * stepX, (row + 0.5f) * stepY)
            )
        }

        // Precompute the current liquid-surface score once per frame. The fill
        // threshold is solved from these scores, so visible liquid quantity stays
        // exact even while the top surface bends and oscillates.
        var insideCount = 0
        var minScore = Float.POSITIVE_INFINITY
        var maxScore = Float.NEGATIVE_INFINITY
        for (row in 0 until rows) {
            val ny = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val nx = (column + 0.5f) / columns
                val index = row * columns + column
                if (!BottleMath.isInside(nx, ny) || BottleMath.isOutline(nx, ny)) {
                    scoreGrid[index] = Float.NaN
                    continue
                }
                val score = BottleMath.surfaceScore(
                    x = nx,
                    y = ny,
                    downX = downX,
                    downY = downY,
                    phase = surface.phase,
                    secondaryPhase = surface.secondaryPhase,
                    secondaryMix = surface.secondaryMix,
                    waveAmplitude = waveAmplitude,
                    meniscusAmplitude = meniscusAmplitude
                )
                scoreGrid[index] = score
                if (score < minScore) minScore = score
                if (score > maxScore) maxScore = score
                insideCount++
            }
        }

        fun drawFill(fraction: Float, color: Color) {
            if (fraction <= 0f || insideCount <= 0) return
            val threshold = BottleMath.fillThresholdFromScores(
                scores = scoreGrid,
                insideCount = insideCount,
                minScore = minScore,
                maxScore = maxScore,
                fraction = visualFraction(fraction)
            )
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val score = scoreGrid[row * columns + column]
                    if (!score.isNaN() && score >= threshold) {
                        dot(column, row, color)
                    }
                }
            }
        }

        // Bottle and empty interior are made from the same dots as the liquid.
        for (row in 0 until rows) {
            val ny = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val nx = (column + 0.5f) / columns
                if (!BottleMath.isInside(nx, ny)) continue
                dot(
                    column,
                    row,
                    if (BottleMath.isOutline(nx, ny)) outlineColor else ghostColor
                )
            }
        }

        // Bright dots = data/liquid. Empty space stays dim.
        // Over 100%, the next liquid colour starts again from the bottom and
        // progressively replaces the previous full bottle.
        if (type.supportsOverflowLayers && ratio >= 1f) {
            val completed = floor(ratio).toInt().coerceAtLeast(1)
            val nextFraction = (ratio - completed).coerceIn(0f, 1f)
            val baseColor = if (completed % 2 == 1) BottlePrimary else BottleOverflow
            val nextColor = if (baseColor == BottlePrimary) BottleOverflow else BottlePrimary
            drawFill(1f, baseColor)
            drawFill(nextFraction, nextColor)
        } else {
            drawFill(ratio.coerceIn(0f, 1f), BottlePrimary)
        }

        // Edge stays visible, but never brighter than the liquid.
        for (row in 0 until rows) {
            val ny = (row + 0.5f) / rows
            for (column in 0 until columns) {
                val nx = (column + 0.5f) / columns
                if (!BottleMath.isOutline(nx, ny)) continue
                dot(column, row, outlineColor)
            }
        }
    }
}
