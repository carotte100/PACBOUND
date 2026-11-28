package com.maxim.paclearn.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/** Le cap du telephone en degres (0 = nord), pour orienter le regard du joueur. */
class Boussole(context: Context, private val onCap: (Float) -> Unit) : SensorEventListener {

    private val capteurs = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = capteurs.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrice = FloatArray(9)
    private val orientation = FloatArray(3)
    private var capLisse = Float.NaN
    private var capAnnonce = Float.NaN

    fun demarrer() {
        rotation?.let { capteurs.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun arreter() = capteurs.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrice, event.values)
        SensorManager.getOrientation(matrice, orientation)
        val cap = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

        // Lissage : 12 % vers la nouvelle valeur, et on ne previent qu'au-dela de 1,5 degre.
        capLisse = if (capLisse.isNaN()) cap else (capLisse + 0.12f * ecart(capLisse, cap) + 360f) % 360f
        if (capAnnonce.isNaN() || abs(ecart(capAnnonce, capLisse)) > 1.5f) {
            capAnnonce = capLisse
            onCap(capLisse)
        }
    }

    /** Ecart par le plus court chemin : de 359 a 1 degre, c'est +2. */
    private fun ecart(de: Float, vers: Float): Float {
        var delta = vers - de
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
