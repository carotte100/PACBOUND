package com.maxim.paclearn

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil

/** Dessine les orbes a ramasser, par-dessus la carte. */
class OrbOverlay(orbs: List<GeoPoint>, density: Float) : Overlay() {

    // Copie modifiable : les orbes ramasses en sont retires.
    private val orbs = orbs.toMutableList()

    // Dessinee une fois puis recopiee a chaque orbe : il y en a des centaines.
    private val coin = drawCoin(4.5f * density)
    private val half = coin.width / 2f
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        for (orb in orbs) {
            projection.toPixels(orb, point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < -half || x > canvas.width + half || y < -half || y > canvas.height + half) continue
            canvas.drawBitmap(coin, x - half, y - half, null)
        }
    }

    /**
     * Retire les orbes situes a moins de [radius] metres de [position] et les
     * renvoie, pour que l'appelant les compte et s'en souvienne.
     */
    fun collectNear(position: GeoPoint, radius: Double): List<GeoPoint> {
        // filter garde les orbes pour lesquels la condition entre { } est vraie.
        val near = orbs.filter { orb -> position.distanceToAsDouble(orb) <= radius }
        // Retires de la liste, ils ne sont plus dessines au prochain draw.
        orbs.removeAll(near)
        return near
    }
}

/**
 * Une piece doree cerclee de blanc, posee sur une ombre : le blanc ressort sur
 * le vert de la carte, l'ombre sur le gris clair des rues.
 */
private fun drawCoin(radius: Float): Bitmap {
    val size = ceil(radius * 3.2f).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = Color.argb(90, 0, 0, 0)
    canvas.drawCircle(c, c + radius * 0.12f, radius * 1.5f, paint)

    paint.color = Color.WHITE
    canvas.drawCircle(c, c, radius * 1.3f, paint)

    paint.shader = RadialGradient(
        c - radius * 0.3f, c - radius * 0.3f, radius * 1.2f,
        Color.rgb(255, 224, 102), Color.rgb(255, 179, 0),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(c, c, radius, paint)
    paint.shader = null

    paint.color = Color.argb(180, 255, 255, 255)
    canvas.drawCircle(c - radius * 0.35f, c - radius * 0.35f, radius * 0.28f, paint)
    return bitmap
}
