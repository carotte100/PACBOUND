package com.maxim.paclearn

import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

object OrbGenerator {

    fun alongRoads(roads: List<List<GeoPoint>>, spacingMeters: Double): List<Orb> {
        val occupiedCells = HashSet<Long>()
        val orbs = ArrayList<Orb>()

        for (road in roads) {
            var sinceLastOrb = 0.0
            for (i in 0 until road.size - 1) {
                val start = road[i]
                val end = road[i + 1]
                val segmentLength = start.distanceToAsDouble(end)
                if (segmentLength == 0.0) continue

                var offset = spacingMeters - sinceLastOrb
                while (offset <= segmentLength) {
                    val t = offset / segmentLength
                    val position = GeoPoint(
                        start.latitude + (end.latitude - start.latitude) * t,
                        start.longitude + (end.longitude - start.longitude) * t
                    )
                    // Les rues partagent leurs noeuds aux intersections : on evite
                    // d'empiler plusieurs orbes au meme carrefour.
                    if (occupiedCells.add(cellKey(position))) {
                        orbs.add(Orb(position))
                    }
                    offset += spacingMeters
                }
                sinceLastOrb = segmentLength - (offset - spacingMeters)
            }
        }
        return orbs
    }

    // Cellules d'environ 10 m de cote.
    private fun cellKey(p: GeoPoint): Long {
        val lat = (p.latitude * 10_000).roundToInt().toLong()
        val lon = (p.longitude * 10_000).roundToInt().toLong()
        return (lat shl 32) or (lon and 0xFFFFFFFFL)
    }
}
