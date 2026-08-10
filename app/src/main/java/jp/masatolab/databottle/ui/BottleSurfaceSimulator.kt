package jp.masatolab.databottle.ui

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Frame-rate-corrected liquid-surface simulation for DATA BOTTLE.
 *
 * v0.1.7 keeps the surface perfectly clean at rest, rounds tiny settled-angle noise, and makes each new slosh
 * subtly different. A new motion event receives a small random phase offset,
 * a random secondary-wave phase, and tiny variations in wave mix/speed.
 * Randomization only happens after the liquid has fully settled, so there is
 * never a visible phase jump in the middle of an ongoing slosh.
 */
class BottleSurfaceSimulator(
    private val config: Config = Config(),
    private val random: Random = Random.Default
) {
    data class Config(
        val sensorFollow: Float = 0.12f,
        val springStrength: Float = 0.035f,
        val springDamping: Float = 0.82f,
        val maxVelocityComponent: Float = 0.18f,
        val tiltEpsilon: Float = 0.015f,
        val velocityEpsilon: Float = 0.010f,
        val maxWaveAmplitudeDots: Float = 4.5f,
        val waveFollow: Float = 0.18f,
        val waveDecay: Float = 0.90f,
        val motionTiltWeight: Float = 0.8f,
        val motionToWaveGain: Float = 18f,
        val phaseSpeed: Float = 0.22f,
        val waveSnapEpsilonDots: Float = 0.05f,

        // v0.1.7: once fully settled, tiny device-angle noise is rounded to
        // the nearest screen cardinal. 0.025 ~= 1.43 degrees.
        val stillCardinalSnapComponent: Float = 0.025f,

        // v0.1.6 organic variation. Deliberately subtle: the user should feel
        // that the liquid is alive without seeing obvious randomness.
        val primaryPhaseJitterMax: Float = 0.40f,
        val secondaryMixMin: Float = 0.25f,
        val secondaryMixMax: Float = 0.35f,
        val phaseSpeedJitter: Float = 0.10f,
        val amplitudeJitter: Float = 0.06f
    )

    data class Frame(
        val downX: Float,
        val downY: Float,
        val waveAmplitudeDots: Float,
        val phase: Float,
        val secondaryPhase: Float,
        val secondaryMix: Float,
        val moving: Boolean
    )

    private var targetX = 0f
    private var targetY = 1f
    private var smoothedX = 0f
    private var smoothedY = 1f
    private var fluidX = 0f
    private var fluidY = 1f
    private var velocityX = 0f
    private var velocityY = 0f
    private var waveAmplitudeDots = 0f
    private var phase = 0f

    // Parameters chosen once per fully-settled -> moving transition.
    private var secondaryPhase = 0f
    private var secondaryMix = 0.30f
    private var eventPhaseSpeedMultiplier = 1f
    private var eventAmplitudeMultiplier = 1f
    private var armedForNewSlosh = true

    fun setTarget(downX: Float, downY: Float) {
        val length = sqrt(downX * downX + downY * downY)
        if (length > 0.001f) {
            targetX = downX / length
            targetY = downY / length
        }
    }

    fun reset(downX: Float = 0f, downY: Float = 1f) {
        val length = sqrt(downX * downX + downY * downY).coerceAtLeast(0.001f)
        targetX = downX / length
        targetY = downY / length
        smoothedX = targetX
        smoothedY = targetY
        fluidX = targetX
        fluidY = targetY
        velocityX = 0f
        velocityY = 0f
        waveAmplitudeDots = 0f
        phase = 0f
        secondaryPhase = 0f
        secondaryMix = 0.30f
        eventPhaseSpeedMultiplier = 1f
        eventAmplitudeMultiplier = 1f
        armedForNewSlosh = true
    }

    fun update(deltaSeconds: Float): Frame {
        val frameScale = (deltaSeconds * 60f).coerceIn(0.25f, 2f)

        // First remove tiny sensor jitter, then let the rendered liquid lag behind.
        val sensorFollow = dtAdjustedFollow(config.sensorFollow, frameScale)
        smoothedX += (targetX - smoothedX) * sensorFollow
        smoothedY += (targetY - smoothedY) * sensorFollow
        normalizeSmoothed()

        val errorX = smoothedX - fluidX
        val errorY = smoothedY - fluidY

        velocityX += errorX * config.springStrength * frameScale
        velocityY += errorY * config.springStrength * frameScale

        val damping = powApprox(config.springDamping, frameScale)
        velocityX = (velocityX * damping)
            .coerceIn(-config.maxVelocityComponent, config.maxVelocityComponent)
        velocityY = (velocityY * damping)
            .coerceIn(-config.maxVelocityComponent, config.maxVelocityComponent)

        fluidX += velocityX * frameScale
        fluidY += velocityY * frameScale
        normalizeFluid()

        val lag = sqrt(errorX * errorX + errorY * errorY)
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        val sensorMotion = lag >= config.tiltEpsilon || speed >= config.velocityEpsilon

        // A new organic wave personality is chosen only after the previous slosh
        // has fully settled. That avoids an abrupt waveform jump while moving.
        if (sensorMotion && armedForNewSlosh) {
            beginNewSlosh()
            armedForNewSlosh = false
        }

        val motion = speed + lag * config.motionTiltWeight
        val targetWaveDots = if (sensorMotion) {
            (motion * config.motionToWaveGain * eventAmplitudeMultiplier)
                .coerceIn(0f, config.maxWaveAmplitudeDots)
        } else {
            0f
        }

        if (targetWaveDots > 0f) {
            val follow = dtAdjustedFollow(config.waveFollow, frameScale)
            waveAmplitudeDots += (targetWaveDots - waveAmplitudeDots) * follow
        } else {
            waveAmplitudeDots *= powApprox(config.waveDecay, frameScale)
            if (waveAmplitudeDots < config.waveSnapEpsilonDots) {
                waveAmplitudeDots = 0f
            }
        }

        if (waveAmplitudeDots > 0f) {
            val strength = (waveAmplitudeDots / config.maxWaveAmplitudeDots).coerceIn(0f, 1f)
            phase += config.phaseSpeed * eventPhaseSpeedMultiplier * frameScale *
                (0.60f + strength * 0.75f)
            phase = wrapPhase(phase)
        }

        // Once motion and the residual wave have died out, remove all visual noise.
        val fullyStill = !sensorMotion && waveAmplitudeDots == 0f
        if (fullyStill) {
            fluidX = smoothedX
            fluidY = smoothedY
            velocityX = 0f
            velocityY = 0f
            armedForNewSlosh = true
        }

        val displayDirection = if (fullyStill) {
            snapToCardinalIfClose(fluidX, fluidY)
        } else {
            fluidX to fluidY
        }

        return Frame(
            downX = displayDirection.first,
            downY = displayDirection.second,
            waveAmplitudeDots = waveAmplitudeDots,
            phase = phase,
            secondaryPhase = secondaryPhase,
            secondaryMix = secondaryMix,
            moving = !fullyStill
        )
    }


    private fun snapToCardinalIfClose(x: Float, y: Float): Pair<Float, Float> {
        val epsilon = config.stillCardinalSnapComponent
        return when {
            abs(x) < epsilon -> 0f to if (y >= 0f) 1f else -1f
            abs(y) < epsilon -> (if (x >= 0f) 1f else -1f) to 0f
            else -> x to y
        }
    }

    private fun beginNewSlosh() {
        // Main wave starts almost where it last stopped, but with enough jitter
        // that repeating the same wrist movement does not look mechanically looped.
        phase = wrapPhase(
            phase + randomRange(-config.primaryPhaseJitterMax, config.primaryPhaseJitterMax)
        )

        // The higher-frequency component receives a much wider independent phase.
        secondaryPhase = randomRange(-PI_F, PI_F)
        secondaryMix = randomRange(config.secondaryMixMin, config.secondaryMixMax)
        eventPhaseSpeedMultiplier =
            1f + randomRange(-config.phaseSpeedJitter, config.phaseSpeedJitter)
        eventAmplitudeMultiplier =
            1f + randomRange(-config.amplitudeJitter, config.amplitudeJitter)
    }

    private fun randomRange(min: Float, max: Float): Float =
        min + (max - min) * random.nextFloat()

    private fun wrapPhase(value: Float): Float {
        var wrapped = value % TWO_PI
        if (wrapped < -PI_F) wrapped += TWO_PI
        if (wrapped > PI_F) wrapped -= TWO_PI
        return wrapped
    }

    private fun normalizeSmoothed() {
        val length = sqrt(smoothedX * smoothedX + smoothedY * smoothedY)
        if (length > 0.001f) {
            smoothedX /= length
            smoothedY /= length
        }
    }

    private fun normalizeFluid() {
        val length = sqrt(fluidX * fluidX + fluidY * fluidY)
        if (length > 0.001f) {
            fluidX /= length
            fluidY /= length
        }
    }

    private fun dtAdjustedFollow(baseFollow: Float, frameScale: Float): Float =
        1f - powApprox(1f - baseFollow, frameScale)

    private fun powApprox(base: Float, exponent: Float): Float =
        Math.pow(base.toDouble(), exponent.toDouble()).toFloat()

    private companion object {
        const val PI_F = Math.PI.toFloat()
        const val TWO_PI = (Math.PI * 2.0).toFloat()
    }
}
