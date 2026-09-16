package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class OrbOverlay(private val orbs: List<Orb>, private val radiusPx: Float) : Overlay() {

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 7)
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 81, 0)
        style = Paint.Style.STROKE
        strokeWidth = radiusPx * 0.35f
    }
    private val haloRadius = radiusPx * 1.4f
    private val screenPoint = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val width = canvas.width
        val height = canvas.height
        for (orb in orbs) {
            projection.toPixels(orb.position, screenPoint)
            val x = screenPoint.x.toFloat()
            val y = screenPoint.y.toFloat()
            if (x < -haloRadius || x > width + haloRadius || y < -haloRadius || y > height + haloRadius) continue
            canvas.drawCircle(x, y, haloRadius, halo)
            canvas.drawCircle(x, y, radiusPx, fill)
            canvas.drawCircle(x, y, radiusPx, outline)
        }
    }
}
