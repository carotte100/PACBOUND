package com.maxim.paclearn.controller

import com.maxim.paclearn.controller.storage.TuileStorage
import com.maxim.paclearn.controller.storage.ZoneStorage
import com.maxim.paclearn.model.Tuile
import com.maxim.paclearn.model.Zone
import com.maxim.paclearn.model.cadreAutour
import com.maxim.paclearn.model.cadreDesTuiles
import com.maxim.paclearn.model.decouper
import com.maxim.paclearn.model.tuilesDe
import org.osmdroid.util.GeoPoint

/** D'ou viennent les rues d'une partie. */
enum class Origine { ENREGISTREE, RESEAU, SECOURS }

class RuesChargees(val zone: Zone, val origine: Origine)

/**
 * Les rues autour de [centre], dans cet ordre :
 * 1. une zone enregistree recente, ou le quartier telecharge, qui couvre tout le carre (pas besoin de reseau) ;
 * 2. le telechargement, enregistre pour la prochaine fois ;
 * 3. hors ligne, ce qu'on a de mieux, meme ancien ou incomplet.
 * Renvoie null si rien de tout cela : la partie passe en mode sans plan.
 */
fun chargerRues(
    zones: ZoneStorage,
    tuiles: TuileStorage,
    centre: GeoPoint,
    demiCote: Double,
    userAgent: String
): RuesChargees? {
    val cadre = cadreAutour(centre, demiCote)
    zones.recente(cadre)?.let { enregistree ->
        lireOuSupprimer(zones, enregistree)?.let { return RuesChargees(it, Origine.ENREGISTREE) }
    }
    tuiles.completes(cadre)?.let { return RuesChargees(it, Origine.ENREGISTREE) }
    try {
        val json = telechargerJson(cadre, userAgent)
        val zone = lireZone(json)
        zones.enregistrer(cadre, json)
        return RuesChargees(zone, Origine.RESEAU)
    } catch (e: Exception) {
        // pas de reseau ou serveurs indisponibles : on passe a ce qui est enregistre
    }
    zones.secours(centre)?.let { enregistree ->
        lireOuSupprimer(zones, enregistree)?.let { return RuesChargees(it, Origine.SECOURS) }
    }
    tuiles.partielles(cadre)?.let { return RuesChargees(it, Origine.SECOURS) }
    return null
}

/** Un fichier abime (telechargement coupe, disque plein) est supprime plutot que de bloquer la partie. */
private fun lireOuSupprimer(stock: ZoneStorage, enregistree: ZoneStorage.Enregistree): Zone? =
    try {
        lireZone(stock.lire(enregistree))
    } catch (e: Exception) {
        stock.supprimer(enregistree)
        null
    }

/**
 * Telecharge le carre de [coteKm] km autour de [centre] et l'enregistre en tuiles.
 * Par morceaux de 4 x 4 tuiles (environ 4,5 km) : une requete par morceau, pour rester
 * leger a lire sur le telephone. [progression] recoit (morceaux faits, total).
 * Renvoie le nombre de morceaux qui ont echoue (0 = tout est enregistre).
 */
fun telechargerQuartier(
    tuiles: TuileStorage,
    centre: GeoPoint,
    coteKm: Double,
    userAgent: String,
    progression: (Int, Int) -> Unit
): Int {
    val morceaux = tuilesDe(cadreAutour(centre, coteKm * 500))
        .groupBy { Tuile(it.i.floorDiv(4), it.j.floorDiv(4)) }
        .values.toList()
    var echecs = 0
    morceaux.forEachIndexed { n, morceau ->
        try {
            val zone = lireZone(telechargerJson(cadreDesTuiles(morceau), userAgent, delaiS = 120))
            tuiles.enregistrer(decouper(zone, morceau))
        } catch (e: Exception) {
            echecs++
        }
        progression(n + 1, morceaux.size)
    }
    return echecs
}
