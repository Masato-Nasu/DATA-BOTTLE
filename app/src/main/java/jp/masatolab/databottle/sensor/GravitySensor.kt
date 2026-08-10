package jp.masatolab.databottle.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt


data class GravityVector(val x: Float, val y: Float, val z: Float) {
    fun canvasDown(): Pair<Float, Float> {
        /*
         * A stationary Android accelerometer / gravity sensor points opposite to
         * physical gravity. Device +X is right and +Y is toward the top edge.
         * Compose +X is right and +Y is down. Negating physical gravity and then
         * converting Y to canvas coordinates gives (-sensorX, +sensorY).
         *
         * This is deliberately the opposite of v0.1.0, where the data settled
         * at the top of the bottle on an upright phone.
         */
        val cx = -x
        val cy = y
        val length = sqrt(cx * cx + cy * cy)
        return if (length < 0.35f) {
            0f to 1f
        } else {
            (cx / length) to (cy / length)
        }
    }
}

@Composable
fun rememberGravityVector(): GravityVector {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var gravity by remember { mutableStateOf(GravityVector(0f, 9.81f, 0f)) }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            private var sx = gravity.x
            private var sy = gravity.y
            private var sz = gravity.z

            override fun onSensorChanged(event: SensorEvent) {
                val alpha = 0.12f
                sx += (event.values[0] - sx) * alpha
                sy += (event.values[1] - sy) * alpha
                sz += (event.values[2] - sz) * alpha
                gravity = GravityVector(sx, sy, sz)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var registered = false
        fun register() {
            if (!registered && sensor != null) {
                registered = sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }
        fun unregister() {
            if (registered) {
                sensorManager.unregisterListener(listener)
                registered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            unregister()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return gravity
}
