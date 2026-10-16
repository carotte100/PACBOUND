package com.maxim.paclearn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.storage.Catalogue
import com.maxim.paclearn.storage.Reserve
import kotlin.random.Random

// Prix et frequence des cartes selon leur rarete. Les communes sortent dix fois plus que les epiques.
// Une piece vaut ORB_VALUE (2 €) : une partie de 5 min paie une ou deux communes, une de 30 min une epique.
private val PRIX_CARTE = mapOf("commune" to 4, "peu commune" to 6, "rare" to 10, "epique" to 15)
private val POIDS_RARETE = mapOf("commune" to 50, "peu commune" to 30, "rare" to 15, "epique" to 5)

fun prixCarte(carte: Carte) = PRIX_CARTE[carte.rarete.lowercase()] ?: 6

enum class EtatOffre { EN_VENTE, VENDUE, RESERVEE }

data class Offre(val carte: Carte, val etat: EtatOffre = EtatOffre.EN_VENTE)

/**
 * La boutique d'une partie : les offres en vitrine, les ameliorations achetees et la reserve.
 * Les ameliorations ne durent que la partie ; les cartes achetees vont dans la collection
 * et les cartes reservees attendent la prochaine partie.
 */
class RunShop(private val catalogue: Catalogue, private val reserve: Reserve) {

    val cartes = mutableStateListOf<Offre>()
    val ameliorations = mutableStateListOf<Amelioration?>()
    val actives = mutableStateListOf<Amelioration>()
    val reservees = mutableStateListOf<Carte>()

    init {
        val ids = reserve.ids()
        reservees.addAll(catalogue.cartes.filter { it.uniqueId() in ids })
        remplir()
    }

    val reservePleine get() = reservees.size >= Reserve.MAX

    fun relancer() = remplir()

    fun vendreCarte(index: Int) {
        cartes[index] = cartes[index].copy(etat = EtatOffre.VENDUE)
    }

    fun reserverCarte(index: Int) {
        if (reservePleine) return
        reservees.add(cartes[index].carte)
        cartes[index] = cartes[index].copy(etat = EtatOffre.RESERVEE)
        sauverReserve()
    }

    fun retirerDeLaReserve(carte: Carte) {
        reservees.removeAll { it.uniqueId() == carte.uniqueId() }
        sauverReserve()
    }

    fun vendreAmelioration(index: Int) {
        ameliorations[index]?.let { actives.add(it) }
        ameliorations[index] = null
    }

    private fun sauverReserve() = reserve.save(reservees.map { it.uniqueId() })

    private fun remplir() {
        cartes.clear()
        repeat(NB_CARTES) {
            // Ni doublon en vitrine, ni carte deja mise en reserve.
            tirerCarte(exclues = cartes.map { it.carte } + reservees)?.let { cartes.add(Offre(it)) }
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
     * Chaque effet des ameliorations actives (ex. "chance de shield" : 15%) a sa chance de
     * reserver l'emplacement a une carte qui a ce trait. Sinon, tirage normal par rarete.
     */
    private fun tirerCarte(exclues: List<Carte>): Carte? {
        val pool = catalogue.cartes.filter { c -> exclues.none { it.uniqueId() == c.uniqueId() } }
        val effets = actives.flatMap { it.effets.entries }.shuffled()
        for ((cle, valeur) in effets) {
            val pourcent = valeur.removeSuffix("%").trim().toIntOrNull() ?: continue
            if (Random.nextInt(100) >= pourcent) continue
            val trait = cle.removePrefix("chance de ").removePrefix("chance d'").trim()
            val ciblees = pool.filter { aLeTrait(it, trait) }
            if (ciblees.isNotEmpty()) return tirerSelonRarete(ciblees) { it.rarete }
        }
        return tirerSelonRarete(pool) { it.rarete }
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

    private companion object {
        const val NB_CARTES = 3
        const val NB_AMELIORATIONS = 2
    }
}

/** Les traits nommes par les effets : domaine, type, rarete, stats ou mot-cle / tag. */
private fun aLeTrait(carte: Carte, trait: String): Boolean = when (trait) {
    "power" -> carte.power > 0
    "unite mighty" -> carte.type == "unite" && carte.might >= MIGHTY
    carte.couleur.lowercase(), carte.type.lowercase(), carte.rarete.lowercase() -> true
    else -> trait in carte.motsCles.lowercase().split(",") || trait in carte.tag.lowercase().split(",")
}

private const val MIGHTY = 5 // dans Riftbound, une unite est "mighty" a partir de 5 de might
const val PRIX_RELANCE = 1

@Composable
fun ShopScreen(
    shop: RunShop,
    money: Int,
    possedees: (Carte) -> Int,
    onAcheterCarte: (Int) -> Unit,
    onAcheterReservee: (Carte) -> Unit,
    onAcheterAmelioration: (Int) -> Unit,
    onRelancer: () -> Unit,
    onFermer: () -> Unit
) {
    BackHandler(onBack = onFermer)

    Column(
        Modifier
            .fillMaxSize()
            .background(RiftBlue)
            .clickable(enabled = false) {} // la carte dessous ne doit pas recevoir les appuis
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BOUTIQUE",
                color = Color.White,
                fontFamily = Title,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(40.dp)
                    .border(1.dp, RiftLine)
                    .clickable(onClick = onFermer),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }
        Text("$money € disponibles", color = RiftOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)

        TitreSection("CARTES")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shop.cartes.forEachIndexed { i, offre ->
                Column(Modifier.weight(1f)) {
                    when (offre.etat) {
                        EtatOffre.VENDUE -> EmplacementVide("Vendue", Modifier.fillMaxWidth())
                        EtatOffre.RESERVEE -> EmplacementVide("Réservée", Modifier.fillMaxWidth())
                        EtatOffre.EN_VENTE -> {
                            CarteVierge(offre.carte)
                            MentionPossedee(possedees(offre.carte))
                            BoutonPrix(prixCarte(offre.carte), money) { onAcheterCarte(i) }
                            Spacer(Modifier.height(6.dp))
                            BoutonSecondaire("Réserver", actif = !shop.reservePleine) { shop.reserverCarte(i) }
                        }
                    }
                }
            }
        }

        TitreSection("RÉSERVE  ${shop.reservees.size} / ${Reserve.MAX}")
        if (shop.reservees.isEmpty()) {
            Text(
                "Réserve jusqu'à ${Reserve.MAX} cartes pour les acheter lors d'une prochaine partie.",
                color = Muted,
                fontSize = 13.sp
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shop.reservees.forEach { carte ->
                    Column(Modifier.weight(1f)) {
                        CarteVierge(carte)
                        MentionPossedee(possedees(carte))
                        BoutonPrix(prixCarte(carte), money) { onAcheterReservee(carte) }
                        Spacer(Modifier.height(6.dp))
                        BoutonSecondaire("Retirer", actif = true) { shop.retirerDeLaReserve(carte) }
                    }
                }
                // Garde la meme largeur de carte qu'avec une reserve pleine.
                repeat(Reserve.MAX - shop.reservees.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        TitreSection("AMÉLIORATIONS")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shop.ameliorations.forEachIndexed { i, amelioration ->
                if (amelioration == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, RiftLine.copy(alpha = 0.5f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Acheté", color = Muted, fontSize = 13.sp)
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(RiftDeep)
                            .border(1.dp, RiftLine)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DescriptionAmelioration(amelioration, Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        BoutonPrix(amelioration.prix, money, Modifier.width(76.dp)) { onAcheterAmelioration(i) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.dp, RiftLine)
                .clickable(enabled = money >= PRIX_RELANCE, onClick = onRelancer)
                .alpha(if (money >= PRIX_RELANCE) 1f else 0.4f),
            contentAlignment = Alignment.Center
        ) {
            Text("Relancer la vitrine · $PRIX_RELANCE €", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (shop.actives.isNotEmpty()) {
            TitreSection("ACTIVES CETTE PARTIE")
            shop.actives.forEach { amelioration ->
                DescriptionAmelioration(amelioration, Modifier.padding(vertical = 8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(RiftLine.copy(alpha = 0.6f))
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TitreSection(titre: String) {
    Text(
        titre,
        color = Color.White,
        fontFamily = Title,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
    )
}

@Composable
private fun DescriptionAmelioration(amelioration: Amelioration, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            amelioration.text.replaceFirstChar { it.uppercase() },
            color = Color.White,
            fontFamily = Title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(amelioration.rarete.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        amelioration.effets.forEach { (effet, valeur) ->
            Text("+$valeur ${effet.replaceFirstChar { it.uppercase() }}", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BoutonPrix(prix: Int, money: Int, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val abordable = money >= prix
    Box(
        modifier
            .height(38.dp)
            .background(if (abordable) RiftOrange else Color.Transparent)
            .border(1.dp, if (abordable) RiftOrange else RiftLine)
            .clickable(enabled = abordable, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$prix €",
            color = if (abordable) Color.White else Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BoutonSecondaire(texte: String, actif: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .border(1.dp, RiftLine)
            .clickable(enabled = actif, onClick = onClick)
            .alpha(if (actif) 1f else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        Text(texte, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun MentionPossedee(deja: Int) {
    Text(
        if (deja > 0) "Possédée ×$deja" else "Nouvelle",
        color = if (deja > 0) Muted else RiftOrange,
        fontSize = 11.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun EmplacementVide(texte: String, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(63f / 88f)
            .border(1.dp, RiftLine.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(texte, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
