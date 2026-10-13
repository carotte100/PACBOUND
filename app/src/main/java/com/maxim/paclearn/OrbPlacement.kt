package com.maxim.paclearn

import org.osmdroid.util.GeoPoint
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val SIDEWALK_M = 2.0 // du bord de la chaussee au milieu du trottoir
private const val CURB_M = 0.5     // tout au bord, quand le trottoir est pris
private const val MIN_GAP_M = 25.0 // entre deux orbes de rues differentes
private const val CELL_M = 20.0

/**
 * Pose un orbe tous les [spacing] metres le long des rues, dans le carre de
 * [halfSize] metres de cote autour de [center].
 *
 * OSM ne connait que l'axe des rues : un orbe pose dessus tombe au milieu de
 * la chaussee. On le decale donc sur le trottoir, d'un cote puis de l'autre en
 * alternance, et on essaie plusieurs places jusqu'a en trouver une qui ne soit
 * ni dans un batiment ni sur une chaussee. S'il n'y en a aucune, pas d'orbe.
 */
fun placeOrbs(area: Area, center: GeoPoint, halfSize: Double, spacing: Double): List<GeoPoint> {
    val plane = LocalPlane(center)
    val obstacles = Obstacles(area, plane, halfSize)
    // Aux carrefours et sur les places, plusieurs rues se touchent : sans
    // ecart minimum, leurs orbes s'y entassaient par deux ou trois.
    val placed = Grid<Vec>(halfSize)
    val orbs = ArrayList<GeoPoint>()

    for (road in area.roads) {
        val offsets = offsetsFrom(road.halfWidth)
        var count = 0
        walk(road.points.map(plane::toXY), spacing) { spot, dirX, dirY ->
            val side = if (count++ % 2 == 0) 1.0 else -1.0
            // (-dirY, dirX) pointe a gauche du sens de la rue.
            val place = offsets.asSequence()
                .map { Vec(spot.x - dirY * it * side, spot.y + dirX * it * side) }
                .firstOrNull { candidate ->
                    candidate.isWithin(halfSize) &&
                        obstacles.isFree(candidate) &&
                        placed.near(candidate).none { it.distanceTo(candidate) < MIN_GAP_M }
                }
            if (place != null) {
                // Range dans toutes les cases a moins de MIN_GAP_M, pour qu'un
                // voisin le trouve en ne regardant que sa propre case.
                placed.add(
                    place,
                    place.x - MIN_GAP_M, place.y - MIN_GAP_M, place.x + MIN_GAP_M, place.y + MIN_GAP_M
                )
                orbs.add(plane.toGeo(place))
            }
        }
    }
    return orbs
}

/** Places essayees, en metres depuis l'axe : le trottoir, puis le bord de la chaussee. */
private fun offsetsFrom(halfWidth: Double): List<Double> {
    if (halfWidth == 0.0) return listOf(0.0)
    val sidewalk = halfWidth + SIDEWALK_M
    val curb = halfWidth + CURB_M
    return listOf(sidewalk, -sidewalk, curb, -curb)
}

/** Appelle [visit] tous les [spacing] metres le long de la ligne, avec sa direction. */
private inline fun walk(
    points: List<Vec>,
    spacing: Double,
    visit: (spot: Vec, dirX: Double, dirY: Double) -> Unit
) {
    var carry = 0.0
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val length = hypot(b.x - a.x, b.y - a.y)
        if (length == 0.0) continue
        val dirX = (b.x - a.x) / length
        val dirY = (b.y - a.y) / length
        var offset = spacing - carry
        while (offset <= length) {
            visit(Vec(a.x + dirX * offset, a.y + dirY * offset), dirX, dirY)
            offset += spacing
        }
        carry = length - (offset - spacing)
    }
}

/** Un troncon de chaussee : un segment de l'axe, elargi de chaque cote. */
private class Lane(val a: Vec, val b: Vec, val halfWidth: Double) {
    fun covers(p: Vec): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        // Projection de p sur le segment, bornee a ses extremites.
        val t = if (lengthSq == 0.0) 0.0
        else (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy)) < halfWidth
    }
}

/** Chaussees et batiments de la zone, la ou un orbe ne doit pas tomber. */
private class Obstacles(area: Area, plane: LocalPlane, halfSize: Double) {
    private val lanes = Grid<Lane>(halfSize)
    private val buildings = Grid<List<Vec>>(halfSize)

    init {
        for (road in area.roads) {
            if (road.halfWidth == 0.0) continue // rue pietonne : on peut s'y tenir
            val points = road.points.map(plane::toXY)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val w = road.halfWidth
                lanes.add(
                    Lane(a, b, w),
                    min(a.x, b.x) - w, min(a.y, b.y) - w, max(a.x, b.x) + w, max(a.y, b.y) + w
                )
            }
        }
        for (outline in area.buildings) {
            val polygon = outline.map(plane::toXY)
            buildings.add(
                polygon,
                polygon.minOf { it.x }, polygon.minOf { it.y },
                polygon.maxOf { it.x }, polygon.maxOf { it.y }
            )
        }
    }

    fun isFree(p: Vec) =
        lanes.near(p).none { it.covers(p) } && buildings.near(p).none { contains(it, p) }
}

/**
 * Lancer de rayon : on part du point vers l'est et on compte les cotes du
 * polygone traverses. Un nombre impair veut dire que le point est dedans.
 */
private fun contains(polygon: List<Vec>, p: Vec): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Range des objets par cases de [CELL_M] metres, pour ne tester que ceux de
 * la case d'un point au lieu de tous ceux de la zone.
 */
private class Grid<T>(halfSize: Double) {
    private val limit = floor(halfSize / CELL_M).toInt() + 1
    private val cells = HashMap<Long, MutableList<T>>()

    fun add(item: T, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        // Les rues debordent souvent de la zone : on ne range que ce qui la touche.
        for (cx in index(minX).coerceAtLeast(-limit)..index(maxX).coerceAtMost(limit)) {
            for (cy in index(minY).coerceAtLeast(-limit)..index(maxY).coerceAtMost(limit)) {
                cells.getOrPut(key(cx, cy)) { ArrayList() }.add(item)
            }
        }
    }

    fun near(p: Vec): List<T> = cells[key(index(p.x), index(p.y))] ?: emptyList()

    private fun index(v: Double) = floor(v / CELL_M).toInt()
    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
}
