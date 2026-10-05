package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/** Le joueur : son pion, et le champ de vision oriente vers ou il regarde. */
class PlayerOverlay(density: Float) : Overlay() {

    var position: GeoPoint? = null
    var heading: Float = 0f

    private val coneRadius = 75 * density
    private val bodyRadius = 9 * density
    private val ringWidth = 3.5f * density

    // Degrade centre sur le joueur : le champ de vision s'estompe avec la distance.
    private val coneShader = RadialGradient(
        0f, 0f, coneRadius,
        intArrayOf(Color.argb(125, 64, 156, 255), Color.argb(0, 64, 156, 255)),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = coneShader }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 120, 230) }

    private val shaderMatrix = Matrix()
    private val coneBounds = RectF()
    private val conePath = Path()
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val here = position ?: return
        projection.toPixels(here, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        shaderMatrix.setTranslate(x, y)
        coneShader.setLocalMatrix(shaderMatrix)

        coneBounds.set(x - coneRadius, y - coneRadius, x + coneRadius, y + coneRadius)
        conePath.rewind()
        conePath.moveTo(x, y)
        // L'azimut part du nord, arcTo part de l'est : d'ou le decalage de 90 degres.
        conePath.arcTo(coneBounds, heading - 90f - CONE_HALF_ANGLE, CONE_HALF_ANGLE * 2f)
        conePath.close()
        canvas.drawPath(conePath, conePaint)

        canvas.drawCircle(x, y, bodyRadius + ringWidth, ringPaint)
        canvas.drawCircle(x, y, bodyRadius, bodyPaint)
    }

    private companion object {
        const val CONE_HALF_ANGLE = 28f
    }
}
