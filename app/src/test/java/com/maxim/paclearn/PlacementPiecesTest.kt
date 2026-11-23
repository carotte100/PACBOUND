package com.maxim.paclearn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.cos

/** Verifie que placerPieces (Rues.kt) pose les pieces au bon endroit, sur un quartier invente. */
class PlacementPiecesTest {

    private val centre = GeoPoint(45.0, 6.0)
    private val metresParDegreLon = 111_320.0 * cos(Math.toRadians(45.0))

    /** Le point a [est] et [nord] metres du centre. */
    private fun a(est: Double, nord: Double) =
        GeoPoint(45.0 + nord / 111_320.0, 6.0 + est / metresParDegreLon)

    private fun estDe(p: GeoPoint) = (p.longitude - 6.0) * metresParDegreLon
    private fun nordDe(p: GeoPoint) = (p.latitude - 45.0) * 111_320.0

    // Une rue est-ouest de 6 m de large, croisee par une rue nord-sud en x = -120,
    // et une rangee de maisons collee au nord de sa moitie ouest.
    private val ruePrincipale = Rue(listOf(a(-300.0, 0.0), a(300.0, 0.0)), demiLargeur = 3.0)
    private val rueTransversale = Rue(listOf(a(-120.0, -300.0), a(-120.0, 300.0)), demiLargeur = 3.0)
    private val maisons = listOf(a(-300.0, 4.0), a(0.0, 4.0), a(0.0, 30.0), a(-300.0, 30.0), a(-300.0, 4.0))

    private fun placer(vararg rues: Rue) =
        placerPieces(Zone(rues.toList(), listOf(maisons)), centre, demiCote = 400.0, espacement = 90.0)

    @Test
    fun pasDePieceSurLaChaussee() {
        val pieces = placer(ruePrincipale, rueTransversale)
        assertTrue(pieces.isNotEmpty())
        for (piece in pieces) {
            assertTrue("piece sur la rue est-ouest : $piece", abs(nordDe(piece)) >= 3.0)
            assertTrue("piece sur la rue nord-sud : $piece", abs(estDe(piece) + 120.0) >= 3.0)
        }
    }

    @Test
    fun pasDePieceDansLesMaisons() {
        for (piece in placer(ruePrincipale, rueTransversale)) {
            val dansUneMaison = estDe(piece) in -300.0..0.0 && nordDe(piece) in 4.0..30.0
            assertFalse("piece dans une maison : $piece", dansUneMaison)
        }
    }

    @Test
    fun lesPiecesAlternentDeCote() {
        // Moitie est, sans maisons : un cote puis l'autre.
        val est = placer(ruePrincipale).filter { estDe(it) > 0 }.sortedBy { estDe(it) }
        assertEquals(3, est.size)
        assertTrue(nordDe(est[0]) * nordDe(est[1]) < 0)
        assertTrue(nordDe(est[1]) * nordDe(est[2]) < 0)
    }

    @Test
    fun ruePietonneLesPiecesRestentAuMilieu() {
        val pietonne = Rue(listOf(a(100.0, -300.0), a(100.0, 300.0)), demiLargeur = 0.0)
        val pieces = placerPieces(Zone(listOf(pietonne), emptyList()), centre, 400.0, 90.0)
        assertTrue(pieces.isNotEmpty())
        pieces.forEach { assertEquals(100.0, estDe(it), 0.01) }
    }

    @Test
    fun ecartMinimumEntreDeuxPieces() {
        // Deux allees pietonnes paralleles a 5 m : sans ecart minimum, leurs
        // pieces tomberaient deux par deux.
        val allee1 = Rue(listOf(a(-300.0, 0.0), a(300.0, 0.0)), demiLargeur = 0.0)
        val allee2 = Rue(listOf(a(-300.0, 5.0), a(300.0, 5.0)), demiLargeur = 0.0)
        val pieces = placerPieces(Zone(listOf(allee1, allee2), emptyList()), centre, 400.0, 90.0)
        for (i in pieces.indices) for (j in i + 1 until pieces.size) {
            assertTrue(pieces[i].distanceToAsDouble(pieces[j]) >= 24.9)
        }
    }

    @Test
    fun pasDePieceHorsDeLaZone() {
        val pieces = placerPieces(Zone(listOf(ruePrincipale), emptyList()), centre, demiCote = 100.0, espacement = 90.0)
        pieces.forEach { assertTrue(abs(estDe(it)) <= 100.0) }
    }
}
