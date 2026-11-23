package com.maxim.paclearn

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.storage.CarteStorage
import com.maxim.paclearn.storage.Catalogue
import com.maxim.paclearn.storage.Reserve
import kotlin.random.Random

// Prix et frequence des cartes selon leur rarete : les communes sortent dix fois plus que les epiques.
// Une piece vaut VALEUR_PIECE (2 €) : une partie de 5 min paie une ou deux communes, une de 30 min une epique.
private val PRIX_CARTE = mapOf("commune" to 4, "peu commune" to 6, "rare" to 10, "epique" to 15)
private val POIDS_RARETE = mapOf("commune" to 50, "peu commune" to 30, "rare" to 15, "epique" to 5)
private const val PRIX_RELANCE = 1
private const val NB_CARTES = 3
private const val NB_AMELIORATIONS = 2
private const val MIGHTY = 5 // dans Riftbound, une unite est "mighty" a partir de 5 de might

private fun prixCarte(carte: Carte) = PRIX_CARTE[carte.rarete.lowercase()] ?: 6

enum class EtatOffre { EN_VENTE, VENDUE, RESERVEE }

data class Offre(val carte: Carte, val etat: EtatOffre = EtatOffre.EN_VENTE)

/**
 * La boutique d'une partie : 3 cartes et 2 ameliorations en vitrine, plus la reserve.
 * Les cartes achetees vont dans la collection, les ameliorations ne durent que la
 * partie, et les cartes reservees attendent la prochaine partie.
 *
 * [payer] retire l'argent du joueur, et renvoie false s'il n'en a pas assez.
 */
class Boutique(
    private val catalogue: Catalogue,
    private val reserve: Reserve,
    private val collection: CarteStorage,
    private val payer: (Int) -> Boolean
) {
    // mutableStateListOf : l'ecran se redessine quand ces listes changent.
    val vitrine = mutableStateListOf<Offre>()
    val ameliorations = mutableStateListOf<Amelioration?>() // null : deja achetee
    val actives = mutableStateListOf<Amelioration>()
    val reservees = mutableStateListOf<Carte>()
    val cartesAchetees = ArrayList<Carte>() // pour le recap

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
            // Ni doublon en vitrine, ni carte deja mise en reserve.
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

    /**
     * Chaque effet des ameliorations actives (ex. "chance de shield" : 15%) a sa chance
     * de reserver l'emplacement a une carte qui a ce trait. Sinon, tirage normal par rarete.
     */
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

    /** Loterie ponderee : avec les poids 50/30/15/5, une commune sort une fois sur deux. */
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

/** Les traits nommes par les effets : domaine, type, rarete, stats ou mot-cle / tag. */
private fun aLeTrait(carte: Carte, trait: String): Boolean = when (trait) {
    "power" -> carte.power > 0
    "unite mighty" -> carte.type == "unite" && carte.might >= MIGHTY
    carte.couleur.lowercase(), carte.type.lowercase(), carte.rarete.lowercase() -> true
    else -> trait in carte.motsCles.lowercase().split(",") || trait in carte.tag.lowercase().split(",")
}

/** L'ecran de la boutique, par-dessus le plan. Chaque bouton appelle une fonction de [boutique]. */
@Composable
fun EcranBoutique(boutique: Boutique, argent: Int, onFermer: () -> Unit) {
    BackHandler(onBack = onFermer)
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titre(stringResource(R.string.boutique), Modifier.weight(1f), grand = true)
                Bouton(stringResource(R.string.fermer), onFermer)
            }
            Text(
                text = stringResource(R.string.argent_disponible, argent),
                color = colorResource(R.color.accent),
                fontWeight = FontWeight.Bold
            )

            TitreSection(stringResource(R.string.cartes))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                boutique.vitrine.forEachIndexed { i, offre ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (offre.etat) {
                            EtatOffre.VENDUE -> EmplacementVide(R.string.vendue)
                            EtatOffre.RESERVEE -> EmplacementVide(R.string.reservee)
                            EtatOffre.EN_VENTE -> {
                                CarteAVendre(offre.carte, boutique, argent) { boutique.acheterCarte(i) }
                                Bouton(
                                    texte = stringResource(R.string.reserver),
                                    onClick = { boutique.reserver(i) },
                                    modifier = Modifier.fillMaxWidth(),
                                    actif = !boutique.reservePleine
                                )
                            }
                        }
                    }
                }
            }

            TitreSection(stringResource(R.string.reserve_titre, boutique.reservees.size, Reserve.MAX))
            if (boutique.reservees.isEmpty()) {
                Text(
                    text = stringResource(R.string.reserve_vide, Reserve.MAX),
                    color = colorResource(R.color.texte_discret),
                    fontSize = 13.sp
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    boutique.reservees.forEach { carte ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CarteAVendre(carte, boutique, argent) { boutique.acheterReservee(carte) }
                            Bouton(
                                texte = stringResource(R.string.retirer),
                                onClick = { boutique.retirerDeLaReserve(carte) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Garde la meme largeur de carte qu'avec une reserve pleine.
                    repeat(Reserve.MAX - boutique.reservees.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            TitreSection(stringResource(R.string.ameliorations))
            boutique.ameliorations.forEachIndexed { i, amelioration ->
                if (amelioration == null) {
                    Text(
                        text = stringResource(R.string.achetee),
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = colorResource(R.color.texte_discret)
                    )
                } else {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DescriptionAmelioration(amelioration, Modifier.weight(1f))
                        Bouton(
                            texte = stringResource(R.string.euros, amelioration.prix),
                            onClick = { boutique.acheterAmelioration(i) },
                            principal = true,
                            actif = argent >= amelioration.prix
                        )
                    }
                }
                HorizontalDivider(color = colorResource(R.color.bordure))
            }

            Bouton(
                texte = stringResource(R.string.relancer, PRIX_RELANCE),
                onClick = boutique::relancer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                actif = argent >= PRIX_RELANCE
            )

            if (boutique.actives.isNotEmpty()) {
                TitreSection(stringResource(R.string.actives))
                boutique.actives.forEach { amelioration ->
                    DescriptionAmelioration(amelioration, Modifier.padding(vertical = 8.dp))
                    HorizontalDivider(color = colorResource(R.color.bordure))
                }
            }
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    Titre(texte, Modifier.padding(top = 24.dp, bottom = 8.dp))
}

/** Une carte en vente : son visuel, si on l'a deja, et le bouton pour l'acheter. */
@Composable
private fun CarteAVendre(carte: Carte, boutique: Boutique, argent: Int, onAcheter: () -> Unit) {
    VisuelCarte(carte)
    val deja = boutique.nbPossedees(carte)
    Text(
        text = if (deja > 0) stringResource(R.string.possedee, deja) else stringResource(R.string.nouvelle),
        color = colorResource(if (deja > 0) R.color.texte_discret else R.color.accent),
        fontSize = 11.sp
    )
    val prix = prixCarte(carte)
    Bouton(
        texte = stringResource(R.string.euros, prix),
        onClick = onAcheter,
        modifier = Modifier.fillMaxWidth(),
        principal = true,
        actif = argent >= prix
    )
}

@Composable
private fun DescriptionAmelioration(amelioration: Amelioration, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(amelioration.text.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
        Text(amelioration.rarete.uppercase(), color = colorResource(R.color.texte_discret), fontSize = 10.sp)
        amelioration.effets.forEach { (effet, valeur) ->
            Text(stringResource(R.string.effet, valeur, effet.replaceFirstChar { it.uppercase() }), fontSize = 12.sp)
        }
    }
}

/** L'emplacement d'une carte vendue ou reservee. */
@Composable
private fun EmplacementVide(@StringRes texte: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(63f / 88f)
            .border(1.dp, colorResource(R.color.bordure)),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(texte), color = colorResource(R.color.texte_discret), fontSize = 13.sp)
    }
}
