package com.maxim.paclearn.view

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.R
import com.maxim.paclearn.controller.Boutique
import com.maxim.paclearn.controller.EtatOffre
import com.maxim.paclearn.controller.PRIX_RELANCE
import com.maxim.paclearn.controller.prixCarte
import com.maxim.paclearn.controller.storage.Reserve
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte

/** L'ecran de la boutique, par-dessus le plan. Chaque bouton appelle [boutique]. */
@Composable
fun EcranBoutique(boutique: Boutique, argent: Int, onFermer: () -> Unit) {
    BackHandler(onBack = onFermer)
    Fond(Modifier.fillMaxSize()) {
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
                    // Meme largeur de carte qu'avec une reserve pleine.
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

/** Une carte en vente : visuel, deja possedee ou non, bouton d'achat. */
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
            .border(1.dp, colorResource(R.color.bordure), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(texte), color = colorResource(R.color.texte_discret), fontSize = 13.sp)
    }
}
