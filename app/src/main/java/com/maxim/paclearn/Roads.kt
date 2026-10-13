package com.maxim.paclearn

import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
private const val LANE_WIDTH_M = 3.0

/** Une rue praticable : son axe, tel qu'OSM le trace, et la demi-largeur de sa chaussee. */
class Road(val points: List<GeoPoint>, val halfWidth: Double)

/** Ce qu'il faut connaitre d'une zone pour y poser les orbes. */
class Area(val roads: List<Road>, val buildings: List<List<GeoPoint>>)

/** Rues praticables et batiments de la zone, via l'API Overpass d'OpenStreetMap. */
fun fetchArea(bbox: BoundingBox, userAgent: String): Area {
    // Une union de valeurs exactes plutot qu'une regex : la regex force
    // Overpass a parcourir toutes les voies et finit en timeout.
    val area = "${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast}"
    val clauses = WALKABLE.joinToString("\n") { """way["highway"="$it"]($area);""" }
    // Les batiments sortent sans leurs tags (skel) : seule leur forme sert, et
    // c'est ce qui les distingue des rues dans la reponse.
    val query = """
        [out:json][timeout:25];
        (
        $clauses
        );
        out geom;
        way["building"]($area);
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
