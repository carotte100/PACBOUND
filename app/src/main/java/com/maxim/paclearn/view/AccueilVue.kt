package com.maxim.paclearn.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import com.maxim.paclearn.R

private val DUREES = listOf(5, 10, 15, 30) // minutes

@Composable
fun EcranAccueil(
    onJouer: (minutes: Int) -> Unit,
    onCollection: () -> Unit,
    coteQuartierKm: Int,
    etatQuartier: String,
    quartierEnCours: Boolean,
    onQuartier: () -> Unit,
    onTriche: () -> Unit
) {
    Fond(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Logo()
            Text(
                stringResource(R.string.slogan),
                color = colorResource(R.color.texte_discret),
                textAlign = TextAlign.Center
            )

            Bloc(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Intitule(stringResource(R.string.duree_partie))
                DUREES.chunked(2).forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ligne.forEach { minutes ->
                            Bouton(
                                texte = stringResource(R.string.duree_minutes, minutes),
                                onClick = { onJouer(minutes) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                principal = true
                            )
                        }
                    }
                }
            }

            Bloc(Modifier.fillMaxWidth()) {
                Bouton(
                    texte = stringResource(R.string.ma_collection),
                    onClick = onCollection,
                    modifier = Modifier.fillMaxWidth()
                )
                Bouton(
                    texte = stringResource(R.string.telecharger_quartier, coteQuartierKm),
                    onClick = onQuartier,
                    modifier = Modifier.fillMaxWidth(),
                    actif = !quartierEnCours
                )
                Text(etatQuartier, color = colorResource(R.color.texte_discret), fontSize = 13.sp)
            }
        }

        // Discret, en bas a gauche : pour les tests.
        TextButton(
            onClick = onTriche,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .systemBarsPadding()
                .padding(4.dp)
        ) {
            Text(stringResource(R.string.triche), color = colorResource(R.color.texte_discret), fontSize = 12.sp)
        }
    }
}

/** Le titre du jeu, avec une piece doree devant. */
@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(26.dp)
                .background(colorResource(R.color.piece), CircleShape)
                .border(BorderStroke(3.dp, colorResource(R.color.piece_bord)), CircleShape)
        )
        Titre(stringResource(R.string.app_name), grand = true)
    }
}

/** Le menu de triche, pour tester la boutique et la collection. Sans style, expres. */
@Composable
fun FenetreTriche(
    bonusArgent: Int,
    message: String,
    onArgent: () -> Unit,
    onCarte: () -> Unit,
    onToutesLesCartes: () -> Unit,
    onViderCollection: () -> Unit,
    onFermer: () -> Unit
) {
    Dialog(onDismissRequest = onFermer) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Triche")
                Text("Argent au début de la prochaine partie : $bonusArgent €")
                Button(onClick = onArgent) { Text("+100 € à la prochaine partie") }
                Button(onClick = onCarte) { Text("+1 carte au hasard") }
                Button(onClick = onToutesLesCartes) { Text("+1 de chaque carte") }
                Button(onClick = onViderCollection) { Text("Vider la collection") }
                if (message.isNotEmpty()) Text(message)
                Button(onClick = onFermer) { Text("Fermer") }
            }
        }
    }
}
