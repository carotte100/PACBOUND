package com.maxim.paclearn.view

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxim.paclearn.R
import kotlinx.coroutines.delay

/** Ce qui s'affiche par-dessus le plan : chrono, argent, boutons et messages. */
@Composable
fun Hud(
    tempsRestant: Int,
    argent: Int,
    @StringRes message: Int?,
    onBoutique: () -> Unit,
    onQuitter: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panneau {
                    Text(
                        text = formatTemps(tempsRestant),
                        color = colorResource(if (tempsRestant <= 30) R.color.alerte else R.color.texte),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Bouton(stringResource(R.string.quitter), onQuitter)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panneau {
                    Text(
                        text = stringResource(R.string.euros, argent),
                        color = colorResource(R.color.accent),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Bouton(stringResource(R.string.boutique), onBoutique, principal = true)
            }
        }

        if (message != null) {
            Panneau(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                if (message == R.string.recherche_position || message == R.string.chargement_plan) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(message), fontSize = 14.sp)
            }
        }

        MentionOsm(Modifier.align(Alignment.BottomStart))
    }
}

/** Mention de licence OpenStreetMap : repliee apres 5 s, un appui sur ⓘ la rouvre. */
@Composable
private fun MentionOsm(modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(5_000)
            visible = false
        }
    }
    Text(
        text = stringResource(if (visible) R.string.attribution_osm else R.string.info),
        modifier = modifier
            .clickable { visible = true }
            .padding(4.dp),
        color = colorResource(R.color.fond),
        fontSize = 12.sp
    )
}
