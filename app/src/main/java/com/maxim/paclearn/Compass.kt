package com.maxim.paclearn

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Cap du telephone en degres, 0 = nord. S'appuie sur le vecteur de rotation
 * plutot que sur le capteur d'orientation, obsolete et absent de certains
 * telephones. Suppose l'ecran en portrait.
 */
class Compass(context: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var smoothed = Float.NaN
    private var reported = Float.NaN

    fun start() {
        rotationVector?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val raw = (degrees + 360f) % 360f

        // Le capteur est nerveux : on avance par petits pas vers la valeur
        // visee, et on ne previent l'ecran que si le cap a vraiment bouge.
        smoothed = if (smoothed.isNaN()) raw else smoothed + SMOOTHING * shortestTurn(smoothed, raw)
        smoothed = (smoothed + 360f) % 360f

        if (reported.isNaN() || kotlin.math.abs(shortestTurn(reported, smoothed)) > REPORT_STEP) {
            reported = smoothed
            onHeading(smoothed)
        }
    }

    /** Ecart entre deux caps, en passant par le plus court chemin (-180..180). */
    private fun shortestTurn(from: Float, to: Float): Float {
        var delta = to - from
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SMOOTHING = 0.12f
        const val REPORT_STEP = 1.5f
    }
}
