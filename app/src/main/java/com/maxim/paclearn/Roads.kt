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

/** Trace des rues praticables dans la zone, via l'API Overpass d'OpenStreetMap. */
fun fetchRoads(bbox: BoundingBox, userAgent: String): List<List<GeoPoint>> {
    // Une union de valeurs exactes plutot qu'une regex : la regex force
    // Overpass a parcourir toutes les voies et finit en timeout.
    val area = "${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast}"
    val clauses = WALKABLE.joinToString("\n") { """way["highway"="$it"]($area);""" }
    val query = """
        [out:json][timeout:25];
        (
        $clauses
        );
        out geom;
    """.trimIndent()

    var failure: Exception? = null
    for (endpoint in OVERPASS_ENDPOINTS) {
        try {
            return parseRoads(post(endpoint, query, userAgent))
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

private fun parseRoads(json: String): List<List<GeoPoint>> {
    val elements = JSONObject(json).getJSONArray("elements")
    return (0 until elements.length()).mapNotNull { i ->
        val geometry = elements.getJSONObject(i).optJSONArray("geometry") ?: return@mapNotNull null
        (0 until geometry.length()).map { j ->
            val node = geometry.getJSONObject(j)
            GeoPoint(node.getDouble("lat"), node.getDouble("lon"))
        }.takeIf { it.size >= 2 }
    }
}

/** Un orbe tous les [spacing] metres le long des rues. */
fun orbsAlong(roads: List<List<GeoPoint>>, spacing: Double): List<GeoPoint> {
    val seen = HashSet<Long>()
    val orbs = ArrayList<GeoPoint>()
    for (road in roads) {
        var carry = 0.0
        for (i in 0 until road.size - 1) {
            val a = road[i]
            val b = road[i + 1]
            val length = a.distanceToAsDouble(b)
            if (length == 0.0) continue
            var offset = spacing - carry
            while (offset <= length) {
                val t = offset / length
                val orb = GeoPoint(
                    a.latitude + (b.latitude - a.latitude) * t,
                    a.longitude + (b.longitude - a.longitude) * t
                )
                // Les rues partagent leurs noeuds aux carrefours : une cellule
                // d'environ 10 m evite d'y empiler plusieurs orbes.
                val cell = (Math.round(orb.latitude * 1e4) shl 32) or
                        (Math.round(orb.longitude * 1e4) and 0xFFFFFFFFL)
                if (seen.add(cell)) orbs.add(orb)
                offset += spacing
            }
            carry = length - (offset - spacing)
        }
    }
    return orbs
}
