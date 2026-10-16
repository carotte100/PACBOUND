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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte

/** Ce qui s'est passe pendant la partie, pour l'ecran de fin. */
data class RunStats(
    val secondesJouees: Int,
    val orbes: Int,
    val gagne: Int,
    val depense: Int,
    val restant: Int,
    val cartes: List<Carte>,
    val ameliorations: List<Amelioration>,
    val tempsEcoule: Boolean
)

/** Avant d'abandonner : l'argent et les ameliorations ne survivent pas a la partie. */
@Composable
fun QuitDialog(money: Int, ameliorations: Int, onRester: () -> Unit, onQuitter: () -> Unit) {
    Dialog(onDismissRequest = onRester) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(RiftBlue)
                .border(1.dp, RiftLine)
                .padding(20.dp)
        ) {
            Text(
                "QUITTER LA PARTIE ?",
                color = Color.White,
                fontFamily = Title,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("Tu vas perdre :", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("• $money € accumulés", color = RiftOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (ameliorations > 0) {
                Text(
                    "• $ameliorations amélioration${if (ameliorations > 1) "s" else ""} active${if (ameliorations > 1) "s" else ""}",
                    color = RiftOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Les cartes achetées et ta réserve sont conservées.",
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoutonPlein("Continuer", plein = true, modifier = Modifier.weight(1f), onClick = onRester)
                BoutonPlein("Quitter", plein = false, modifier = Modifier.weight(1f), onClick = onQuitter)
            }
        }
    }
}

@Composable
fun RecapScreen(stats: RunStats, onTerminer: () -> Unit) {
    BackHandler(onBack = onTerminer)

    Column(
        Modifier
            .fillMaxSize()
            .background(RiftBlue)
            .clickable(enabled = false) {}
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            if (stats.tempsEcoule) "TEMPS ÉCOULÉ" else "PARTIE TERMINÉE",
            color = Color.White,
            fontFamily = Title,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text("Récapitulatif de la partie", color = Muted, fontSize = 13.sp)

        Spacer(Modifier.height(20.dp))
        Ligne("Durée jouée", "%d:%02d".format(stats.secondesJouees / 60, stats.secondesJouees % 60))
        Ligne("Pièces ramassées", "${stats.orbes}")
        Ligne("Argent gagné", "${stats.gagne} €")
        Ligne("Argent dépensé", "${stats.depense} €")
        Ligne("Argent perdu", "${stats.restant} €", alerte = stats.restant > 0)

        Titre("CARTES OBTENUES")
        if (stats.cartes.isEmpty()) {
            Text("Aucune carte achetée cette partie.", color = Muted, fontSize = 13.sp)
        } else {
            stats.cartes.chunked(3).forEach { rangee ->
                Row(
                    Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rangee.forEach { CarteVierge(it, Modifier.weight(1f)) }
                    repeat(3 - rangee.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        if (stats.ameliorations.isNotEmpty()) {
            Titre("AMÉLIORATIONS UTILISÉES")
            stats.ameliorations.forEach {
                Text("• ${it.text.replaceFirstChar { c -> c.uppercase() }}", color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        BoutonPlein("Retour à l'accueil", plein = true, modifier = Modifier.fillMaxWidth(), onClick = onTerminer)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Titre(texte: String) {
    Text(
        texte,
        color = Color.White,
        fontFamily = Title,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp)
    )
}

@Composable
private fun Ligne(label: String, valeur: String, alerte: Boolean = false) {
    Column {
        Row(Modifier.padding(vertical = 9.dp)) {
            Text(label, color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                valeur,
                color = if (alerte) RiftOrange else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RiftLine.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun BoutonPlein(texte: String, plein: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .background(if (plein) RiftOrange else Color.Transparent)
            .border(1.dp, if (plein) RiftOrange else RiftLine)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(texte, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
