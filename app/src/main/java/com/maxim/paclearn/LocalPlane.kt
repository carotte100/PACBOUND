package com.maxim.paclearn

import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

private const val METERS_PER_DEGREE = 111_320.0

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
