package com.maxim.paclearn

import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val OVERPASS = "https://overpass-api.de/api/interpreter"
private const val WALKABLE =
    "primary|secondary|tertiary|residential|unclassified|living_street|pedestrian|footway|path|cycleway|service"

/** Trace des rues praticables dans la zone, via l'API Overpass d'OpenStreetMap. */
fun fetchRoads(bbox: BoundingBox, userAgent: String): List<List<GeoPoint>> {
    val query = """
        [out:json][timeout:25];
        way["highway"~"^($WALKABLE)${'$'}"](${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast});
        out geom;
    """.trimIndent()

    val json = (URL(OVERPASS).openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 30_000
        doOutput = true
        setRequestProperty("User-Agent", userAgent)
        outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
        inputStream.bufferedReader().use { it.readText() }
    }

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
