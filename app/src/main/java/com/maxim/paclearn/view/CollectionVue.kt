package com.maxim.paclearn.view

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.R
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.model.Eclat
import com.maxim.paclearn.model.eclatDe

private enum class Tri(@StringRes val nom: Int) { COUT(R.string.cout), NOM(R.string.nom), RARETE(R.string.rarete) }

private val DOMAINES = listOf("rouge", "vert", "bleu", "orange", "pourpre", "jaune")
private val TYPES = listOf("unite" to R.string.type_unite, "sort" to R.string.type_sort, "equipement" to R.string.type_equipement)
private val RARETES = listOf("commune", "peu commune", "rare", "epique")

/** L'album : toutes les [cartes] du catalogue, grisees tant qu'on ne les possede pas. */
@Composable
fun EcranCollection(cartes: List<Carte>, onRetour: () -> Unit) {
    // Reglages de l'ecran, conserves quand il se redessine.
    var filtresOuverts by remember { mutableStateOf(false) }
    var tri by remember { mutableStateOf(Tri.COUT) }
    var domaine by remember { mutableStateOf<String?>(null) } // null : tous
    var type by remember { mutableStateOf<String?>(null) }
    var recherche by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Carte?>(null) } // fiche ouverte

    val obtenues = cartes.filter { it.quantity > 0 }
    val parSet = cartes
        .filter { carte ->
            (domaine == null || carte.couleur.equals(domaine, ignoreCase = true)) &&
                (type == null || carte.type.equals(type, ignoreCase = true)) &&
                (recherche.isBlank() || carte.nom.contains(recherche, ignoreCase = true) ||
                    carte.tag.contains(recherche, ignoreCase = true))
        }
        .sortedWith(
            when (tri) {
                Tri.COUT -> compareBy<Carte>({ it.cost }, { it.nom })
                Tri.NOM -> compareBy<Carte> { it.nom }
                Tri.RARETE -> compareByDescending<Carte> { RARETES.indexOf(it.rarete.lowercase()) }.thenBy { it.nom }
            }
        )
        .groupBy { it.set.lowercase() }
        .toSortedMap()

    Fond(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.systemBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Bouton(stringResource(R.string.retour), onRetour)
                    Titre(stringResource(R.string.collection), grand = true)
                    Text(
                        text = stringResource(
                            R.string.collection_compte,
                            obtenues.size, cartes.size, obtenues.sumOf { it.quantity }
                        ),
                        color = colorResource(R.color.texte_discret),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = recherche,
                        onValueChange = { recherche = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.rechercher)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Bouton(
                        texte = stringResource(if (filtresOuverts) R.string.masquer_filtres else R.string.filtres),
                        onClick = { filtresOuverts = !filtresOuverts }
                    )
                    if (filtresOuverts) {
                        Choix(R.string.trier_par, Tri.entries.map { stringResource(it.nom) }, tri.ordinal) {
                            tri = Tri.entries[it]
                        }
                        // Second appui sur le choix actif : on l'enleve.
                        Choix(R.string.domaine, DOMAINES.map { it.replaceFirstChar(Char::uppercase) }, DOMAINES.indexOf(domaine)) {
                            domaine = if (domaine == DOMAINES[it]) null else DOMAINES[it]
                        }
                        Choix(R.string.type, TYPES.map { stringResource(it.second) }, TYPES.indexOfFirst { it.first == type }) {
                            type = if (type == TYPES[it].first) null else TYPES[it].first
                        }
                    }
                }
            }

            if (parSet.isEmpty() && cartes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.aucune_carte_filtre),
                        modifier = Modifier.padding(vertical = 40.dp),
                        color = colorResource(R.color.texte_discret)
                    )
                }
            }

            parSet.forEach { (set, cartesDuSet) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "set_$set") {
                    Titre(
                        stringResource(R.string.set_titre, set, cartesDuSet.count { it.quantity > 0 }, cartesDuSet.size),
                        Modifier.padding(top = 16.dp)
                    )
                }
                items(cartesDuSet, key = { it.uniqueId() }) { carte ->
                    VisuelCarte(
                        carte,
                        Modifier
                            .alpha(if (carte.quantity > 0) 1f else 0.3f)
                            .clickable { detail = carte }
                    )
                }
            }
        }

        detail?.let { FicheCarte(it, onFermer = { detail = null }) }
    }
}

/** Une ligne de boutons dont un seul est choisi (rempli) : [choisi] vaut -1 si aucun. */
@Composable
private fun Choix(@StringRes titre: Int, options: List<String>, choisi: Int, onChoix: (Int) -> Unit) {
    Text(stringResource(titre), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, option ->
            Bouton(option, onClick = { onChoix(i) }, principal = i == choisi)
        }
    }
}

/** La fiche d'une carte, quand on la touche dans l'album. */
@Composable
private fun FicheCarte(carte: Carte, onFermer: () -> Unit) {
    Fenetre(onFermer) {
        VisuelCarte(carte, Modifier.padding(horizontal = 36.dp))
        Titre(carte.nom, Modifier.padding(top = 18.dp))
        Text(
            text = listOf(carte.set, carte.image, carte.rarete).filter { it.isNotEmpty() }
                .joinToString("  ·  ") { it.uppercase() },
            modifier = Modifier.padding(bottom = 14.dp),
            color = colorResource(R.color.texte_discret),
            fontSize = 12.sp
        )
        LigneInfo(stringResource(R.string.domaine), carte.couleur.replaceFirstChar { it.uppercase() })
        LigneInfo(stringResource(R.string.type), TYPES.firstOrNull { it.first == carte.type }?.let { stringResource(it.second) } ?: carte.type)
        LigneInfo(stringResource(R.string.cout), "${carte.cost}")
        LigneInfo(stringResource(R.string.power), "${carte.power}")
        if (carte.type == "unite") LigneInfo(stringResource(R.string.might), "${carte.might}")
        if (carte.motsCles.isNotEmpty()) LigneInfo(stringResource(R.string.mots_cles), carte.motsCles.replace(",", ", "))
        if (carte.tag.isNotEmpty()) LigneInfo(stringResource(R.string.tags), carte.tag.replace(",", ", "))
        if (carte.quantity > 0) {
            val eclat = eclatDe(carte.quantity)
            LigneInfo(stringResource(R.string.eclat), stringResource(nomEclat(eclat)), enAvant = eclat != Eclat.AUCUN)
            eclat.suivant?.let {
                Text(
                    text = stringResource(R.string.eclat_prochain, it.seuil - carte.quantity, stringResource(nomEclat(it))),
                    modifier = Modifier.padding(top = 6.dp),
                    color = colorResource(R.color.texte_discret),
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = if (carte.quantity > 0) stringResource(R.string.exemplaires, carte.quantity)
            else stringResource(R.string.pas_obtenue),
            modifier = Modifier.padding(vertical = 16.dp),
            color = colorResource(if (carte.quantity > 0) R.color.texte else R.color.accent),
            fontWeight = FontWeight.Bold
        )
        Bouton(stringResource(R.string.fermer), onFermer, Modifier.fillMaxWidth())
    }
}

private fun nomEclat(eclat: Eclat) = when (eclat) {
    Eclat.AUCUN -> R.string.eclat_aucun
    Eclat.BRILLANTE -> R.string.eclat_brillante
    Eclat.HOLOGRAPHIQUE -> R.string.eclat_holographique
    Eclat.LEGENDAIRE -> R.string.eclat_legendaire
}
