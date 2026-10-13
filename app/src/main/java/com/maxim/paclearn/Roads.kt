package com.maxim.paclearn

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Les donnees du jeu : rues et batiments telecharges depuis OpenStreetMap,
// puis les orbes poses le long des rues.

// L'instance principale rend regulierement des 504 : on bascule sur un miroir.
private val OVERPASS_ENDPOINTS = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter"
)
// Trottoirs, chemins et voies de parking exclus : OSM les cartographie en
// double de chaque rue, ce qui noie la carte sous les orbes.
private val WALKABLE = listOf(
    "primary", "secondary", "tertiary", "residential", "unclassified",
    "living_street", "pedestrian"
)

private const val METERS_PER_DEGREE = 111_320.0
private const val LANE_WIDTH_M = 3.0
private const val SIDEWALK_M = 2.0 // du bord de la chaussee au milieu du trottoir
private const val CURB_M = 0.5     // tout au bord, quand le trottoir est pris
private const val MIN_GAP_M = 25.0 // entre deux orbes de rues differentes
private const val CELL_M = 20.0

/** Une rue praticable : son axe, tel qu'OSM le trace, et la demi-largeur de sa chaussee. */
class Road(val points: List<GeoPoint>, val halfWidth: Double)

/** Ce qu'il faut connaitre d'une zone pour la dessiner et y poser les orbes. */
class Area(val roads: List<Road>, val buildings: List<List<GeoPoint>>)

/** Rues praticables et batiments du carre de [halfSize] metres autour de [center]. */
fun fetchArea(center: GeoPoint, halfSize: Double, userAgent: String): Area {
    val plane = LocalPlane(center)
    val sw = plane.toGeo(Vec(-halfSize, -halfSize))
    val ne = plane.toGeo(Vec(halfSize, halfSize))
    val bbox = "${sw.latitude},${sw.longitude},${ne.latitude},${ne.longitude}"
    // Une union de valeurs exactes plutot qu'une regex : la regex force
    // Overpass a parcourir toutes les voies et finit en timeout.
    val clauses = WALKABLE.joinToString("\n") { """way["highway"="$it"]($bbox);""" }
    // Les batiments sortent sans leurs tags (skel) : seule leur forme sert, et
    // c'est ce qui les distingue des rues dans la reponse.
    val query = """
        [out:json][timeout:25];
        (
        $clauses
        );
        out geom;
        way["building"]($bbox);
        out skel geom;
    """.trimIndent()

    var failure: Exception? = null
    for (endpoint in OVERPASS_ENDPOINTS) {
        try {
            return parseArea(post(endpoint, query, userAgent))
        } catch (e: Exception) {
            failure = e
        }
    }
    throw failure ?: IOException("Aucun serveur Overpass joignable")
}

private fun post(endpoint: String, query: String, userAgent: String): String {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 30_000
        doOutput = true
        setRequestProperty("User-Agent", userAgent)
    }
    try {
        connection.outputStream.use {
            it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray())
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Overpass ${connection.responseCode} sur $endpoint")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun parseArea(json: String): Area {
    val roads = ArrayList<Road>()
    val buildings = ArrayList<List<GeoPoint>>()
    val elements = JSONObject(json).getJSONArray("elements")
    for (i in 0 until elements.length()) {
        val element = elements.getJSONObject(i)
        val geometry = element.optJSONArray("geometry") ?: continue
        val points = (0 until geometry.length()).map { j ->
            val node = geometry.getJSONObject(j)
            GeoPoint(node.getDouble("lat"), node.getDouble("lon"))
        }
        val tags = element.optJSONObject("tags")
        when {
            tags != null && points.size >= 2 -> roads.add(Road(points, halfWidthOf(tags)))
            tags == null && points.size >= 3 -> buildings.add(points)
        }
    }
    return Area(roads, buildings)
}

/**
 * OSM ne trace que l'axe des rues : pour trouver leurs bords, on prend la
 * largeur si elle est renseignee, sinon le nombre de voies, sinon une valeur
 * typique du type de rue.
 */
private fun halfWidthOf(tags: JSONObject): Double {
    val highway = tags.optString("highway")
    // Rue pietonne ou partagee : on marche au milieu, pas de bord a chercher.
    if (highway == "pedestrian" || highway == "living_street") return 0.0

    // "width" s'ecrit parfois "6 m" : on ne garde que le nombre en tete.
    tags.optString("width").takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()
        ?.let { return it / 2 }
    tags.optString("lanes").toIntOrNull()?.let { return it * LANE_WIDTH_M / 2 }
    return when (highway) {
        "primary" -> 5.0
        "secondary" -> 4.5
        "tertiary" -> 4.0
        else -> 3.0
    }
}

/**
 * Identifiant de la cellule d'environ 10 m qui contient [point]. Une meme rue
 * regenere ses orbes aux memes coordonnees, donc a la meme cellule : c'est ce
 * qui permet de reconnaitre un orbe deja ramasse apres un rechargement.
 */
fun cellOf(point: GeoPoint): Long =
    (Math.round(point.latitude * 1e4) shl 32) or (Math.round(point.longitude * 1e4) and 0xFFFFFFFFL)

/**
 * Pose un orbe tous les [spacing] metres le long des rues, dans le carre de
 * [halfSize] metres autour de [center].
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
        // Places essayees, en metres depuis l'axe : le trottoir, puis le bord de la chaussee.
        val w = road.halfWidth
        val offsets = if (w == 0.0) listOf(0.0)
        else listOf(w + SIDEWALK_M, -(w + SIDEWALK_M), w + CURB_M, -(w + CURB_M))
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
                placed.add(place, place.x - MIN_GAP_M, place.y - MIN_GAP_M, place.x + MIN_GAP_M, place.y + MIN_GAP_M)
                orbs.add(plane.toGeo(place))
            }
        }
    }
    return orbs
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
        val length = a.distanceTo(b)
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

/** Chaussees et batiments de la zone, la ou un orbe ne doit pas tomber. */
private class Obstacles(area: Area, plane: LocalPlane, halfSize: Double) {
    /** Un troncon de chaussee : un segment de l'axe, elargi de chaque cote. */
    private class Lane(val a: Vec, val b: Vec, val halfWidth: Double)

    private val lanes = Grid<Lane>(halfSize)
    private val buildings = Grid<List<Vec>>(halfSize)

    init {
        for (road in area.roads) {
            val w = road.halfWidth
            if (w == 0.0) continue // rue pietonne : on peut s'y tenir
            val points = road.points.map(plane::toXY)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                lanes.add(Lane(a, b, w), min(a.x, b.x) - w, min(a.y, b.y) - w, max(a.x, b.x) + w, max(a.y, b.y) + w)
            }
        }
        for (outline in area.buildings) {
            val polygon = outline.map(plane::toXY)
            buildings.add(
                polygon,
                polygon.minOf { it.x }, polygon.minOf { it.y }, polygon.maxOf { it.x }, polygon.maxOf { it.y }
            )
        }
    }

    fun isFree(p: Vec) =
        lanes.near(p).none { distanceToSegment(p, it.a, it.b) < it.halfWidth } &&
            buildings.near(p).none { contains(it, p) }

    private fun distanceToSegment(p: Vec, a: Vec, b: Vec): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        // Projection de p sur le segment, bornee a ses extremites.
        val t = if (lengthSq == 0.0) 0.0
        else (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
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

internal class Vec(val x: Double, val y: Double) {
    fun isWithin(halfSize: Double) = abs(x) <= halfSize && abs(y) <= halfSize
    fun distanceTo(other: Vec) = hypot(x - other.x, y - other.y)
}

/**
 * Coordonnees en metres autour d'un point, x vers l'est et y vers le nord. A
 * l'echelle d'un quartier, la courbure de la Terre est negligeable : les
 * distances deviennent de la geometrie plane.
 */
internal class LocalPlane(val origin: GeoPoint) {
    private val metersPerLon = METERS_PER_DEGREE * cos(Math.toRadians(origin.latitude))

    fun toXY(p: GeoPoint) = Vec(
        (p.longitude - origin.longitude) * metersPerLon,
        (p.latitude - origin.latitude) * METERS_PER_DEGREE
    )

    fun toGeo(v: Vec) = GeoPoint(
        origin.latitude + v.y / METERS_PER_DEGREE,
        origin.longitude + v.x / metersPerLon
    )
}
