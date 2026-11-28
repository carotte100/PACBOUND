package com.maxim.paclearn.controller.storage

import android.content.Context
import com.maxim.paclearn.model.Cadre
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Les zones chargees pendant les parties, gardees dans files/zones (compressees) pour jouer sans internet.
 * Nom de fichier : zone_<jour>_<sud>_<ouest>_<nord>_<est>.gz
 */
class ZoneStorage(context: Context) {

    class Enregistree(val fichier: File, val cadre: Cadre, val jour: Long)

    private val dossier = File(context.filesDir, "zones").apply { mkdirs() }

    fun enregistrer(cadre: Cadre, json: String) {
        val nom = "zone_${aujourdhui()}_${cadre.sud}_${cadre.ouest}_${cadre.nord}_${cadre.est}"
        val temporaire = File(dossier, "$nom.tmp")
        GZIPOutputStream(temporaire.outputStream()).bufferedWriter().use { it.write(json) }
        temporaire.renameTo(File(dossier, "$nom.gz"))
        nettoyer()
    }

    /** Une zone de moins de [MAX_AGE_JOURS] jours qui couvre tout [cadre] : utilisable sans attendre le reseau. */
    fun recente(cadre: Cadre): Enregistree? =
        liste().filter { aujourdhui() - it.jour <= MAX_AGE_JOURS && it.cadre.couvre(cadre) }.maxByOrNull { it.jour }

    /** Hors ligne : la zone, meme ancienne, qui contient [p] et en est le mieux centree. */
    fun secours(p: GeoPoint): Enregistree? =
        liste().filter { it.cadre.contient(p) }.minByOrNull { it.cadre.ecartAuCentre(p) }

    fun lire(zone: Enregistree): String {
        zone.fichier.setLastModified(System.currentTimeMillis()) // les zones servies restent au nettoyage
        return GZIPInputStream(zone.fichier.inputStream()).bufferedReader().use { it.readText() }
    }

    fun supprimer(zone: Enregistree) {
        zone.fichier.delete()
    }

    fun octets() = liste().sumOf { it.fichier.length() }

    private fun liste(): List<Enregistree> =
        dossier.listFiles { f -> f.name.endsWith(".gz") }.orEmpty().mapNotNull(::analyser)

    private fun analyser(fichier: File): Enregistree? {
        val parties = fichier.name.removeSuffix(".gz").split("_")
        if (parties.size != 6 || parties[0] != "zone") return null
        val jour = parties[1].toLongOrNull() ?: return null
        val n = parties.drop(2).map { it.toDoubleOrNull() ?: return null }
        return Enregistree(fichier, Cadre(n[0], n[1], n[2], n[3]), jour)
    }

    /** Au-dela de [MAX_OCTETS], les zones les moins servies partent en premier. */
    private fun nettoyer() {
        val zones = liste()
        var total = zones.sumOf { it.fichier.length() }
        for (zone in zones.sortedBy { it.fichier.lastModified() }) {
            if (total <= MAX_OCTETS) break
            total -= zone.fichier.length()
            zone.fichier.delete()
        }
    }

    private fun aujourdhui() = System.currentTimeMillis() / 86_400_000L

    companion object {
        const val MAX_AGE_JOURS = 30
        const val MAX_OCTETS = 50L * 1024 * 1024
    }
}
