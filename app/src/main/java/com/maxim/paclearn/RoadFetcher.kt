package com.maxim.paclearn

import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RoadFetcher {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    private val WALKABLE_HIGHWAYS = listOf(
        "primary", "secondary", "tertiary", "residential", "unclassified",
        "living_street", "pedestrian", "footway", "path", "cycleway", "service"
    )

    fun fetchRoads(bbox: BoundingBox, userAgent: String): List<List<GeoPoint>> {
        val highwayFilter = WALKABLE_HIGHWAYS.joinToString("|")
        val query = """
            [out:json][timeout:25];
            way["highway"~"^($highwayFilter)${'$'}"]
              (${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast});
            out geom;
        """.trimIndent()

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            connection.outputStream.use {
                it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Overpass HTTP ${connection.responseCode}")
            }
            return parseRoads(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRoads(json: String): List<List<GeoPoint>> {
        val elements = JSONObject(json).getJSONArray("elements")
        val roads = ArrayList<List<GeoPoint>>(elements.length())
        for (i in 0 until elements.length()) {
            val geometry = elements.getJSONObject(i).optJSONArray("geometry") ?: continue
            val points = ArrayList<GeoPoint>(geometry.length())
            for (j in 0 until geometry.length()) {
                val node = geometry.getJSONObject(j)
                points.add(GeoPoint(node.getDouble("lat"), node.getDouble("lon")))
            }
            if (points.size >= 2) roads.add(points)
        }
        return roads
    }
}
