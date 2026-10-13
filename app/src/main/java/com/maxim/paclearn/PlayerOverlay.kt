package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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

/**
 * Le joueur : son pion, le champ de vision oriente vers ou il regarde, et la
 * zone dans laquelle il ramasse les orbes.
 */
class PlayerOverlay(density: Float, private val collectRadiusM: Double) : Overlay() {

    var position: GeoPoint? = null
    var heading: Float = 0f

    private val coneRadius = 75 * density
    private val faceRadius = 14 * density
    private val ringWidth = 2.5f * density

    // Degrade centre sur le joueur : le champ de vision s'estompe avec la distance.
    private val coneShader = RadialGradient(
        0f, 0f, coneRadius,
        intArrayOf(Color.argb(125, 64, 156, 255), Color.argb(0, 64, 156, 255)),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = coneShader }
    // Bord blanc : il ressort sur le vert de la carte, le remplissage bleu sur le gris des rues.
    private val zoneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 64, 156, 255) }
    private val zoneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        pathEffect = DashPathEffect(floatArrayOf(6 * density, 4 * density), 0f)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 221, 0) }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = faceRadius * 0.16f
        strokeCap = Paint.Cap.ROUND
    }

    private val shaderMatrix = Matrix()
    private val coneBounds = RectF()
    private val conePath = Path()
    private val smileBounds = RectF()
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val here = position ?: return
        projection.toPixels(here, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        // A l'echelle reelle, calculee a la latitude du joueur : elle suit le zoom.
        val zone = projection.metersToPixels(collectRadiusM.toFloat(), here.latitude, projection.zoomLevel)
        canvas.drawCircle(x, y, zone, zoneFill)
        canvas.drawCircle(x, y, zone, zoneEdge)

        shaderMatrix.setTranslate(x, y)
        coneShader.setLocalMatrix(shaderMatrix)

        coneBounds.set(x - coneRadius, y - coneRadius, x + coneRadius, y + coneRadius)
        conePath.rewind()
        conePath.moveTo(x, y)
        // L'azimut part du nord, arcTo part de l'est : d'ou le decalage de 90 degres.
        conePath.arcTo(coneBounds, heading - 90f - CONE_HALF_ANGLE, CONE_HALF_ANGLE * 2f)
        conePath.close()
        canvas.drawPath(conePath, conePaint)

        drawSmiley(canvas, x, y)
    }

    /** Le visage reste droit : c'est le cone qui indique la direction. */
    private fun drawSmiley(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, faceRadius + ringWidth, ringPaint)
        canvas.drawCircle(x, y, faceRadius, facePaint)

        val eyeX = faceRadius * 0.34f
        val eyeY = y - faceRadius * 0.22f
        canvas.drawOval(
            x - eyeX - faceRadius * 0.12f, eyeY - faceRadius * 0.20f,
            x - eyeX + faceRadius * 0.12f, eyeY + faceRadius * 0.20f,
            eyePaint
        )
        canvas.drawOval(
            x + eyeX - faceRadius * 0.12f, eyeY - faceRadius * 0.20f,
            x + eyeX + faceRadius * 0.12f, eyeY + faceRadius * 0.20f,
            eyePaint
        )

        smileBounds.set(
            x - faceRadius * 0.55f, y - faceRadius * 0.35f,
            x + faceRadius * 0.55f, y + faceRadius * 0.55f
        )
        canvas.drawArc(smileBounds, 25f, 130f, false, smilePaint)
    }

    private companion object {
        const val CONE_HALF_ANGLE = 28f
    }
}
