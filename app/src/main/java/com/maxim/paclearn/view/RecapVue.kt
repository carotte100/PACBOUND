package com.maxim.paclearn.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.R
import com.maxim.paclearn.model.RecapPartie

/** "Quitter ?" : rappelle ce qui sera perdu. */
@Composable
fun FenetreQuitter(argent: Int, nbAmeliorations: Int, onRester: () -> Unit, onQuitter: () -> Unit) {
    Fenetre(onFermer = onRester) {
        Titre(stringResource(R.string.quitter_titre))
        Text(
            text = stringResource(R.string.quitter_perte),
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            color = colorResource(R.color.texte_discret)
        )
        Text(
            text = stringResource(R.string.quitter_argent, argent),
            color = colorResource(R.color.accent),
            fontWeight = FontWeight.Bold
        )
        if (nbAmeliorations > 0) {
            Text(
                text = stringResource(R.string.quitter_ameliorations, nbAmeliorations),
                color = colorResource(R.color.accent),
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(R.string.quitter_conserve),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Bouton(stringResource(R.string.continuer), onRester, Modifier.weight(1f), principal = true)
            Bouton(stringResource(R.string.quitter), onQuitter, Modifier.weight(1f))
        }
    }
}

/** Le bilan de fin de partie. */
@Composable
fun EcranRecap(recap: RecapPartie, onTerminer: () -> Unit) {
    BackHandler(onBack = onTerminer)
    Fond(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Titre(
                texte = stringResource(if (recap.tempsEcoule) R.string.recap_temps_ecoule else R.string.recap_terminee),
                modifier = Modifier.padding(top = 20.dp),
                grand = true
            )
            Text(
                text = stringResource(R.string.recap_sous_titre),
                modifier = Modifier.padding(bottom = 20.dp),
                color = colorResource(R.color.texte_discret),
                fontSize = 13.sp
            )
            LigneInfo(stringResource(R.string.recap_duree), formatTemps(recap.secondesJouees))
            LigneInfo(stringResource(R.string.recap_pieces), "${recap.pieces}")
            LigneInfo(stringResource(R.string.recap_gagne), stringResource(R.string.euros, recap.argentGagne))
            LigneInfo(stringResource(R.string.recap_depense), stringResource(R.string.euros, recap.argentDepense))
            LigneInfo(
                intitule = stringResource(R.string.recap_perdu),
                valeur = stringResource(R.string.euros, recap.argentRestant),
                enAvant = recap.argentRestant > 0
            )

            Titre(stringResource(R.string.recap_cartes), Modifier.padding(top = 28.dp, bottom = 10.dp))
            if (recap.cartes.isEmpty()) {
                Text(
                    text = stringResource(R.string.recap_aucune_carte),
                    color = colorResource(R.color.texte_discret),
                    fontSize = 13.sp
                )
            }
            recap.cartes.chunked(3).forEach { ligne ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ligne.forEach { VisuelCarte(it, Modifier.weight(1f)) }
                    repeat(3 - ligne.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (recap.ameliorations.isNotEmpty()) {
                Titre(stringResource(R.string.recap_ameliorations), Modifier.padding(top = 28.dp, bottom = 10.dp))
                recap.ameliorations.forEach {
                    Text(stringResource(R.string.puce, it.text.replaceFirstChar { c -> c.uppercase() }))
                }
            }

            Bouton(
                texte = stringResource(R.string.retour_accueil),
                onClick = onTerminer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                principal = true
            )
        }
    }
}
