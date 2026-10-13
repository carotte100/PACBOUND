package com.maxim.paclearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.cos

class OrbPlacementTest {

    private val center = GeoPoint(45.0, 6.0)
    private val metersPerLon = 111_320.0 * cos(Math.toRadians(45.0))

    /** Point a [east] et [north] metres du centre. */
    private fun at(east: Double, north: Double) =
        GeoPoint(45.0 + north / 111_320.0, 6.0 + east / metersPerLon)

    private fun eastOf(p: GeoPoint) = (p.longitude - 6.0) * metersPerLon
    private fun northOf(p: GeoPoint) = (p.latitude - 45.0) * 111_320.0

    // Une rue est-ouest de 6 m de large, croisee par une rue nord-sud en x = -120,
    // et une rangee de maisons collee au nord de sa moitie ouest.
    private val mainStreet = Road(listOf(at(-300.0, 0.0), at(300.0, 0.0)), halfWidth = 3.0)
    private val crossStreet = Road(listOf(at(-120.0, -300.0), at(-120.0, 300.0)), halfWidth = 3.0)
    private val houses = listOf(at(-300.0, 4.0), at(0.0, 4.0), at(0.0, 30.0), at(-300.0, 30.0), at(-300.0, 4.0))

    private fun place(vararg roads: Road) =
        placeOrbs(Area(roads.toList(), listOf(houses)), center, halfSize = 400.0, spacing = 90.0)

    @Test
    fun orbsStayOffTheRoads() {
        val orbs = place(mainStreet, crossStreet)
        assertTrue(orbs.isNotEmpty())
        for (orb in orbs) {
            assertTrue("orbe sur la rue est-ouest : $orb", abs(northOf(orb)) >= 3.0)
            assertTrue("orbe sur la rue nord-sud : $orb", abs(eastOf(orb) + 120.0) >= 3.0)
        }
    }

    @Test
    fun orbsStayOutOfBuildings() {
        for (orb in place(mainStreet, crossStreet)) {
            val inHouse = eastOf(orb) in -300.0..0.0 && northOf(orb) in 4.0..30.0
            assertFalse("orbe dans une maison : $orb", inHouse)
        }
    }

    @Test
    fun orbsAlternateSidesWhereBothAreFree() {
        // Moitie est, sans maisons : un cote puis l'autre.
        val east = place(mainStreet).filter { eastOf(it) > 0 }.sortedBy { eastOf(it) }
        assertEquals(3, east.size)
        assertTrue(northOf(east[0]) * northOf(east[1]) < 0)
        assertTrue(northOf(east[1]) * northOf(east[2]) < 0)
    }

    @Test
    fun pedestrianStreetsKeepOrbsOnTheirAxis() {
        val pedestrian = Road(listOf(at(100.0, -300.0), at(100.0, 300.0)), halfWidth = 0.0)
        val orbs = placeOrbs(Area(listOf(pedestrian), emptyList()), center, 400.0, 90.0)
        assertTrue(orbs.isNotEmpty())
        orbs.forEach { assertEquals(100.0, eastOf(it), 0.01) }
    }

    @Test
    fun orbsKeepTheirDistanceWhereStreetsMeet() {
        // Deux allees pietonnes paralleles a 5 m : sans ecart minimum, leurs
        // orbes tomberaient deux par deux.
        val a = Road(listOf(at(-300.0, 0.0), at(300.0, 0.0)), halfWidth = 0.0)
        val b = Road(listOf(at(-300.0, 5.0), at(300.0, 5.0)), halfWidth = 0.0)
        val orbs = placeOrbs(Area(listOf(a, b), emptyList()), center, 400.0, 90.0)
        for (i in orbs.indices) for (j in i + 1 until orbs.size) {
            assertTrue(orbs[i].distanceToAsDouble(orbs[j]) >= 24.9)
        }
    }

    @Test
    fun orbsOutsideTheAreaAreDropped() {
        val orbs = placeOrbs(Area(listOf(mainStreet), emptyList()), center, halfSize = 100.0, spacing = 90.0)
        orbs.forEach { assertTrue(abs(eastOf(it)) <= 100.0) }
    }
}
