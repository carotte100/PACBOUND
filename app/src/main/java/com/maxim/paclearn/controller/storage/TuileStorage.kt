package com.maxim.paclearn.controller.storage

import android.content.Context
import com.maxim.paclearn.model.Cadre
import com.maxim.paclearn.model.Rue
import com.maxim.paclearn.model.Tuile
import com.maxim.paclearn.model.Zone
import com.maxim.paclearn.model.fusionner
import com.maxim.paclearn.model.tuilesDe
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Le quartier telecharge, en tuiles d'environ 1 km dans files/tuiles (une par fichier, compressee).
 * Format d'une ligne : "R <demi-largeur> lat lon lat lon..." pour une rue, "B lat lon..." pour un batiment.
 */
class TuileStorage(context: Context) {

    private val dossier = File(context.filesDir, "tuiles").apply { mkdirs() }

    fun enregistrer(tuiles: Map<Tuile, Zone>) = tuiles.forEach { (tuile, zone) -> ecrire(tuile, zone) }

    /** Les rues de [cadre] si toutes ses tuiles sont enregistrees, sinon null. */
    fun completes(cadre: Cadre): Zone? {
        val tuiles = tuilesDe(cadre)
        if (tuiles.any { !fichier(it).exists() }) return null
        return fusionner(tuiles.mapNotNull(::lire))
    }

    /** Hors ligne : ce qu'on a de [cadre], meme incomplet. */
    fun partielles(cadre: Cadre): Zone? {
        val zones = tuilesDe(cadre).filter { fichier(it).exists() }.mapNotNull(::lire)
        return if (zones.isEmpty()) null else fusionner(zones)
    }

    fun nombre() = fichiers().size

    fun octets() = fichiers().sumOf { it.length() }

    fun vider() = fichiers().forEach { it.delete() }

    private fun fichiers() = dossier.listFiles { f -> f.name.endsWith(".gz") }.orEmpty()

    private fun fichier(t: Tuile) = File(dossier, "t_${t.i}_${t.j}.gz")

    private fun ecrire(tuile: Tuile, zone: Zone) {
        val temporaire = File(dossier, "t_${tuile.i}_${tuile.j}.tmp")
        GZIPOutputStream(temporaire.outputStream()).bufferedWriter().use { w ->
            for (rue in zone.rues) {
                w.write("R ${rue.demiLargeur}")
                rue.points.forEach { w.write(" ${it.latitude} ${it.longitude}") }
                w.newLine()
            }
            for (batiment in zone.batiments) {
                w.write("B")
                batiment.forEach { w.write(" ${it.latitude} ${it.longitude}") }
                w.newLine()
            }
        }
        temporaire.renameTo(fichier(tuile)) // jamais de tuile a moitie ecrite
    }

    private fun lire(tuile: Tuile): Zone? = try {
        val rues = ArrayList<Rue>()
        val batiments = ArrayList<List<GeoPoint>>()
        GZIPInputStream(fichier(tuile).inputStream()).bufferedReader().useLines { lignes ->
            for (ligne in lignes) {
                val m = ligne.split(" ")
                when (m.first()) {
                    "R" -> rues.add(Rue(points(m.drop(2)), m[1].toDouble()))
                    "B" -> batiments.add(points(m.drop(1)))
                }
            }
        }
        Zone(rues, batiments)
    } catch (e: Exception) {
        fichier(tuile).delete() // abimee : on la retelechargera
        null
    }

    private fun points(nombres: List<String>) =
        nombres.chunked(2).map { GeoPoint(it[0].toDouble(), it[1].toDouble()) }
}
