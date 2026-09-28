package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/** Dessine les orbes a ramasser, par-dessus la carte. */
class OrbOverlay(private val orbs: List<GeoPoint>, density: Float) : Overlay() {

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 179, 0) }
    private val radius = 3.5f * density
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        for (orb in orbs) {
            projection.toPixels(orb, point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < 0 || x > canvas.width || y < 0 || y > canvas.height) continue
            canvas.drawCircle(x, y, radius * 1.4f, halo)
            canvas.drawCircle(x, y, radius, fill)
        }
    }
}
