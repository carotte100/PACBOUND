package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

class OrbOverlay(private val orbs: List<Orb>, private val radiusPx: Float) : Overlay() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(184, 134, 11)
        style = Paint.Style.STROKE
        strokeWidth = radiusPx * 0.3f
    }
    private val screenPoint = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val width = canvas.width
        val height = canvas.height
        for (orb in orbs) {
            projection.toPixels(orb.position, screenPoint)
            val x = screenPoint.x.toFloat()
            val y = screenPoint.y.toFloat()
            if (x < -radiusPx || x > width + radiusPx || y < -radiusPx || y > height + radiusPx) continue
            canvas.drawCircle(x, y, radiusPx, fill)
            canvas.drawCircle(x, y, radiusPx, outline)
        }
    }
}
