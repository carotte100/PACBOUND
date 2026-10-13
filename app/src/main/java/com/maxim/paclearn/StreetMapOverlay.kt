package com.maxim.paclearn

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.max

/**
 * La carte du jeu, dessinee a partir des donnees OSM deja chargees pour les
 * orbes : un sol vert, des batiments a peine plus fonces, et les rues en gris
 * clair pour qu'elles sautent aux yeux. Pas de noms, pas de tuiles a telecharger.
 */
class StreetMapOverlay(area: Area, center: GeoPoint, density: Float) : Overlay() {

    private val plane = LocalPlane(center)
    private val roadLayers: List<RoadLayer>
    private val buildings: List<Outline>

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

    init {
        // Un calque par largeur de rue : drawLines n'accepte qu'une epaisseur par appel.
        roadLayers = area.roads
            .groupBy { if (it.halfWidth == 0.0) PEDESTRIAN_WIDTH_M else it.halfWidth * 2 }
            .map { (width, roads) ->
                val segments = ArrayList<Float>()
                for (road in roads) {
                    val points = road.points.map(plane::toXY)
                    for (i in 0 until points.size - 1) {
                        segments += points[i].x.toFloat()
                        segments += points[i].y.toFloat()
                        segments += points[i + 1].x.toFloat()
                        segments += points[i + 1].y.toFloat()
                    }
                }
                RoadLayer(width.toFloat(), segments.toFloatArray())
            }
        buildings = area.buildings.map { outline ->
            Outline(outline.map(plane::toXY).flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray())
        }
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
            roadEdge.strokeWidth = layer.widthPx(scale) + 2 * roadEdgePx
            canvas.drawLines(layer.pixels, roadEdge)
        }
        for (layer in roadLayers) {
            roadFill.strokeWidth = layer.widthPx(scale)
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

    private fun RoadLayer.widthPx(scale: Float) = max(minRoadPx, widthM * scale)

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
