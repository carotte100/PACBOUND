package com.maxim.paclearn.controller

import androidx.compose.runtime.mutableStateListOf
import com.maxim.paclearn.controller.storage.CarteStorage
import com.maxim.paclearn.controller.storage.Catalogue
import com.maxim.paclearn.controller.storage.Reserve
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte
import kotlin.random.Random

// Une piece vaut VALEUR_PIECE (2 €) : 5 min paient une ou deux communes, 30 min une epique.
private val PRIX_CARTE = mapOf("commune" to 4, "peu commune" to 6, "rare" to 10, "epique" to 15)
private val POIDS_RARETE = mapOf("commune" to 50, "peu commune" to 30, "rare" to 15, "epique" to 5)
private const val NB_CARTES = 3
private const val NB_AMELIORATIONS = 2
private const val MIGHTY = 5 // une unite est "mighty" a partir de 5 de might
const val PRIX_RELANCE = 1

fun prixCarte(carte: Carte) = PRIX_CARTE[carte.rarete.lowercase()] ?: 6

enum class EtatOffre { EN_VENTE, VENDUE, RESERVEE }

data class Offre(val carte: Carte, val etat: EtatOffre = EtatOffre.EN_VENTE)

/**
 * La boutique d'une partie : 3 cartes et 2 ameliorations en vitrine, plus la reserve.
 * [payer] retire l'argent du joueur et renvoie false s'il n'en a pas assez.
 */
class Boutique(
    private val catalogue: Catalogue,
    private val reserve: Reserve,
    private val collection: CarteStorage,
    private val payer: (Int) -> Boolean
) {
    // Listes observees par Compose : l'ecran se redessine quand elles changent.
    val vitrine = mutableStateListOf<Offre>()
    val ameliorations = mutableStateListOf<Amelioration?>() // null : deja achetee
    val actives = mutableStateListOf<Amelioration>()
    val reservees = mutableStateListOf<Carte>()
    val cartesAchetees = ArrayList<Carte>()

    init {
        val ids = reserve.ids()
        reservees.addAll(catalogue.cartes.filter { it.uniqueId() in ids })
        remplirVitrine()
    }

    val reservePleine get() = reservees.size >= Reserve.MAX

    fun nbPossedees(carte: Carte) = collection.find(carte.uniqueId())?.quantity ?: 0

    fun acheterCarte(index: Int) {
        val offre = vitrine[index]
        if (offre.etat != EtatOffre.EN_VENTE || !payer(prixCarte(offre.carte))) return
        ajouterALaCollection(offre.carte)
        vitrine[index] = offre.copy(etat = EtatOffre.VENDUE)
    }

    fun acheterReservee(carte: Carte) {
        if (!payer(prixCarte(carte))) return
        ajouterALaCollection(carte)
        retirerDeLaReserve(carte)
    }

    fun acheterAmelioration(index: Int) {
        val amelioration = ameliorations[index] ?: return
        if (!payer(amelioration.prix)) return
        actives.add(amelioration)
        ameliorations[index] = null
    }

    fun relancer() {
        if (payer(PRIX_RELANCE)) remplirVitrine()
    }

    fun reserver(index: Int) {
        if (reservePleine) return
        reservees.add(vitrine[index].carte)
        vitrine[index] = vitrine[index].copy(etat = EtatOffre.RESERVEE)
        sauverReserve()
    }

    fun retirerDeLaReserve(carte: Carte) {
        reservees.removeAll { it.uniqueId() == carte.uniqueId() }
        sauverReserve()
    }

    private fun ajouterALaCollection(carte: Carte) {
        collection.addOrIncrement(carte)
        cartesAchetees.add(carte)
    }

    private fun sauverReserve() = reserve.save(reservees.map { it.uniqueId() })

    private fun remplirVitrine() {
        vitrine.clear()
        repeat(NB_CARTES) {
            // Ni doublon en vitrine, ni carte deja reservee.
            tirerCarte(exclues = vitrine.map { it.carte } + reservees)?.let { vitrine.add(Offre(it)) }
        }
        ameliorations.clear()
        val disponibles = catalogue.ameliorations.filter { a -> actives.none { it.id == a.id } }.toMutableList()
        repeat(NB_AMELIORATIONS) {
            tirerSelonRarete(disponibles) { it.rarete }?.let {
                ameliorations.add(it)
                disponibles.remove(it)
            }
        }
    }

    /** Un effet actif ("chance de shield" : 15%) peut forcer une carte qui a ce trait. */
    private fun tirerCarte(exclues: List<Carte>): Carte? {
        val possibles = catalogue.cartes.filter { c -> exclues.none { it.uniqueId() == c.uniqueId() } }
        for ((effet, valeur) in actives.flatMap { it.effets.entries }.shuffled()) {
            val pourcent = valeur.removeSuffix("%").trim().toIntOrNull() ?: continue
            if (Random.nextInt(100) >= pourcent) continue
            val trait = effet.removePrefix("chance de ").removePrefix("chance d'").trim()
            val ciblees = possibles.filter { aLeTrait(it, trait) }
            if (ciblees.isNotEmpty()) return tirerSelonRarete(ciblees) { it.rarete }
        }
        return tirerSelonRarete(possibles) { it.rarete }
    }

    private fun <T> tirerSelonRarete(choix: List<T>, rarete: (T) -> String): T? {
        if (choix.isEmpty()) return null
        val poids = choix.map { POIDS_RARETE[rarete(it).lowercase()] ?: 10 }
        var tirage = Random.nextInt(poids.sum())
        choix.forEachIndexed { i, element ->
            tirage -= poids[i]
            if (tirage < 0) return element
        }
        return choix.last()
    }
}

private fun aLeTrait(carte: Carte, trait: String): Boolean = when (trait) {
    "power" -> carte.power > 0
    "unite mighty" -> carte.type == "unite" && carte.might >= MIGHTY
    carte.couleur.lowercase(), carte.type.lowercase(), carte.rarete.lowercase() -> true
    else -> trait in carte.motsCles.lowercase().split(",") || trait in carte.tag.lowercase().split(",")
}
