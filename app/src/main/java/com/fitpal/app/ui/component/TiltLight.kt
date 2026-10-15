package com.fitpal.app.ui.component

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.TiltState

/**
 * Liquid glass's light: feeds the phone's tilt into [TiltState] so the glass rims catch the light
 * on the side you tip the phone towards. Listens to the gravity sensor only while the app is on
 * screen, only in the Liquid glass look, and never with "Reduce motion" on — otherwise it costs
 * nothing. Put it once near the root.
 */
@Composable
fun TiltLight() {
    val look = LookState.look
    val active = look.style == LookStyle.LIQUID && !look.appearance.reduceMotion
    val context = LocalContext.current
    LifecycleStartEffect(active) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Gravity along x (tip left/right) and y (tip back/forward), smoothed so the light
                // glides instead of jittering.
                val tx = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val ty = (event.values[1] / SensorManager.GRAVITY_EARTH - 0.6f).coerceIn(-1f, 1f)
                val prev = TiltState.tilt
                TiltState.tilt = Offset(prev.x + (tx - prev.x) * 0.15f, prev.y + (ty - prev.y) * 0.15f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (active && sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            TiltState.tilt = Offset.Zero
        }
        onStopOrDispose { sm?.unregisterListener(listener) }
    }
}
