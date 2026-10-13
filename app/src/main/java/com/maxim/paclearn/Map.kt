package com.maxim.paclearn

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.max

// Tout ce qui se dessine sur la carte, du sol au joueur. Chaque calque est un
// Overlay d'osmdroid, que la MapView dessine dans l'ordre ou on les ajoute.

/**
 * La carte du jeu, dessinee a partir des donnees OSM deja chargees pour les
 * orbes : un sol vert, des batiments a peine plus fonces, et les rues en gris
 * clair pour qu'elles sautent aux yeux. Pas de noms, pas de tuiles a telecharger.
 */
class StreetMapOverlay(area: Area, center: GeoPoint, density: Float) : Overlay() {

    private val plane = LocalPlane(center)
    private val minRoadPx = 3f * density
    private val roadEdgePx = 1.5f * density

    private val roadEdge = strokePaint(ROAD_EDGE)
    private val roadFill = strokePaint(ROAD)
    private val buildingFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BUILDING }
    private val buildingEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BUILDING_EDGE
        style = Paint.Style.STROKE
        strokeWidth = density
    }

    // Reutilises a chaque image : pas d'allocation pendant que la carte bouge.
    private val buildingPath = Path()
    private val point = Point()

    // Un calque par largeur de rue : drawLines n'accepte qu'une epaisseur par appel.
    private val roadLayers = area.roads
        .groupBy { if (it.halfWidth == 0.0) PEDESTRIAN_WIDTH_M else it.halfWidth * 2 }
        .map { (width, roads) ->
            val segments = ArrayList<Float>()
            for (road in roads) {
                val points = road.points.map(plane::toXY)
                for (i in 0 until points.size - 1) {
                    segments += listOf(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y).map { it.toFloat() }
                }
            }
            RoadLayer(width.toFloat(), segments.toFloatArray())
        }

    private val buildings = area.buildings.map { outline ->
        Outline(outline.map(plane::toXY).flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray())
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        // Les donnees sont en metres autour du centre : un decalage et une
        // echelle suffisent pour les poser a l'ecran, quel que soit le zoom.
        projection.toPixels(plane.origin, point)
        val cx = point.x.toFloat()
        val cy = point.y.toFloat()
        val scale = projection.metersToPixels(100f, plane.origin.latitude, projection.zoomLevel) / 100f

        drawBuildings(canvas, cx, cy, scale)

        for (layer in roadLayers) layer.project(cx, cy, scale)
        // Tous les bords d'abord, puis tous les remplissages : aux carrefours,
        // le gris des rues recouvre les bords de celles qu'elles croisent.
        for (layer in roadLayers) {
            roadEdge.strokeWidth = max(minRoadPx, layer.widthM * scale) + 2 * roadEdgePx
            canvas.drawLines(layer.pixels, roadEdge)
        }
        for (layer in roadLayers) {
            roadFill.strokeWidth = max(minRoadPx, layer.widthM * scale)
            canvas.drawLines(layer.pixels, roadFill)
        }
    }

    private fun drawBuildings(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        // Seuls les batiments visibles entrent dans le trace : un trace plus
        // grand que l'ecran peut depasser ce que le GPU sait dessiner.
        val left = -cx / scale
        val right = (canvas.width - cx) / scale
        val top = cy / scale
        val bottom = (cy - canvas.height) / scale

        buildingPath.rewind()
        for (building in buildings) {
            if (building.maxX < left || building.minX > right || building.maxY < bottom || building.minY > top) continue
            val m = building.meters
            buildingPath.moveTo(cx + m[0] * scale, cy - m[1] * scale)
            for (i in 2 until m.size step 2) buildingPath.lineTo(cx + m[i] * scale, cy - m[i + 1] * scale)
            buildingPath.close()
        }
        canvas.drawPath(buildingPath, buildingFill)
        canvas.drawPath(buildingPath, buildingEdge)
    }

    /** Les rues d'une meme largeur, en segments [x1, y1, x2, y2, ...] exprimes en metres. */
    private class RoadLayer(val widthM: Float, private val meters: FloatArray) {
        val pixels = FloatArray(meters.size)

        fun project(cx: Float, cy: Float, scale: Float) {
            for (i in meters.indices step 2) {
                pixels[i] = cx + meters[i] * scale
                // Le nord est en haut : y grandit vers le haut en metres, vers le bas a l'ecran.
                pixels[i + 1] = cy - meters[i + 1] * scale
            }
        }
    }

    private class Outline(val meters: FloatArray) {
        val minX = (meters.indices step 2).minOf { meters[it] }
        val maxX = (meters.indices step 2).maxOf { meters[it] }
        val minY = (1 until meters.size step 2).minOf { meters[it] }
        val maxY = (1 until meters.size step 2).maxOf { meters[it] }
    }

    companion object {
        // Le sol est le fond de la MapView (voir MainActivity) : la carte n'a pas a le couvrir.
        const val GROUND = 0xFF9ED98B.toInt()
        private const val BUILDING = 0xFF8BC77A.toInt()
        private const val BUILDING_EDGE = 0xFF79B568.toInt()
        private const val ROAD = 0xFFE4E8E3.toInt()
        private const val ROAD_EDGE = 0xFFA4B3A1.toInt()

        // OSM ne donne pas la largeur des rues pietonnes : celle d'une ruelle.
        private const val PEDESTRIAN_WIDTH_M = 4.0

        private fun strokePaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            // Bouts ronds : les segments d'une meme rue se raccordent sans cassure.
            strokeCap = Paint.Cap.ROUND
        }
    }
}

/** Les orbes a ramasser, par-dessus la carte. */
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
 * Le joueur : son smiley, le champ de vision oriente vers ou il regarde, et la
 * zone dans laquelle il ramasse les orbes.
 */
class PlayerOverlay(density: Float, private val collectRadiusM: Double) : Overlay() {

    var position: GeoPoint? = null
    var heading: Float = 0f

    private val coneRadius = 75 * density
    private val faceRadius = 14 * density

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

    private val shaderMatrix = Matrix()
    private val coneBounds = RectF()
    private val conePath = Path()
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

        // Le visage reste droit : c'est le cone qui indique la direction.
        drawSmiley(canvas, x, y, faceRadius)
    }

    private companion object {
        const val CONE_HALF_ANGLE = 28f
    }
}

private val smileyPen = Paint(Paint.ANTI_ALIAS_FLAG)

/** Le visage du joueur, sur la carte comme sur l'ecran d'accueil. */
fun drawSmiley(canvas: Canvas, x: Float, y: Float, face: Float) = with(smileyPen) {
    style = Paint.Style.FILL
    color = Color.WHITE
    canvas.drawCircle(x, y, face * 1.18f, this)
    color = Smiley.toArgb()
    canvas.drawCircle(x, y, face, this)

    color = Color.BLACK
    val eyeY = y - face * 0.22f
    for (eyeX in floatArrayOf(x - face * 0.34f, x + face * 0.34f)) {
        canvas.drawOval(eyeX - face * 0.12f, eyeY - face * 0.2f, eyeX + face * 0.12f, eyeY + face * 0.2f, this)
    }
    style = Paint.Style.STROKE
    strokeWidth = face * 0.16f
    strokeCap = Paint.Cap.ROUND
    canvas.drawArc(x - face * 0.55f, y - face * 0.35f, x + face * 0.55f, y + face * 0.55f, 25f, 130f, false, this)
}

/**
 * Une piece doree cerclee de blanc, posee sur une ombre : le blanc ressort sur
 * le vert de la carte, l'ombre sur le gris clair des rues. Sert aussi d'icone
 * a la cagnotte du HUD.
 */
fun drawCoin(radius: Float): Bitmap {
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
