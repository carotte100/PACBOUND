package com.maxim.paclearn

import android.content.Context
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// Les durees de partie proposees, en minutes.
private val DUREES = listOf(5, 10, 15, 30)

/** L'ecran d'accueil : on choisit la duree de la partie, ou on va voir sa collection. */
class AccueilActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        setContent {
            PacTheme { EcranAccueil() }
        }
    }
}

@Composable
private fun EcranAccueil() {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Titre(stringResource(R.string.app_name), grand = true)
            Text(stringResource(R.string.slogan), color = colorResource(R.color.texte_discret))

            Titre(stringResource(R.string.duree_partie), Modifier.padding(top = 24.dp))
            // Deux lignes de deux boutons.
            DUREES.chunked(2).forEach { ligne ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ligne.forEach { minutes ->
                        Bouton(
                            texte = stringResource(R.string.duree_minutes, minutes),
                            onClick = { lancerPartie(context, minutes) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Bouton(
                texte = stringResource(R.string.ma_collection),
                onClick = { context.startActivity(Intent(context, CollectionActivity::class.java)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
        }
    }
}

/** Ouvre l'ecran de jeu, en lui donnant la duree choisie. */
private fun lancerPartie(context: Context, minutes: Int) {
    context.startActivity(
        Intent(context, JeuActivity::class.java).putExtra(JeuActivity.EXTRA_MINUTES, minutes)
    )
}
