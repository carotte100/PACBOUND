package com.maxim.paclearn.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maxim.paclearn.R
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.model.eclatDe

// Les petits elements d'interface partages par tous les ecrans.

/** Titre en majuscules et en serif, comme sur les cartes Riftbound. */
@Composable
fun Titre(texte: String, modifier: Modifier = Modifier, grand: Boolean = false) {
    Text(
        text = texte.uppercase(),
        modifier = modifier,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Black,
        fontSize = if (grand) 32.sp else 20.sp
    )
}

/** Le fond de chaque ecran : bleu nuit, un peu plus sombre vers le bas. */
@Composable
fun Fond(modifier: Modifier = Modifier, contenu: @Composable BoxScope.() -> Unit) {
    Surface(modifier, color = colorResource(R.color.fond)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colorResource(R.color.fond), colorResource(R.color.fond_profond)))),
            content = contenu
        )
    }
}

/** Un bloc aux coins arrondis, un peu plus clair que le fond, pour regrouper un contenu. */
@Composable
fun Bloc(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = colorResource(R.color.surface),
        border = BorderStroke(1.dp, colorResource(R.color.bordure))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = contenu)
    }
}

/** Petit intitule de section, en capitales espacees et en orange. */
@Composable
fun Intitule(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte.uppercase(),
        modifier = modifier,
        color = colorResource(R.color.accent),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp
    )
}

/** Bouton arrondi et plat : orange si [principal], sinon bleu avec un contour. */
@Composable
fun Bouton(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    actif: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = actif,
        shape = RoundedCornerShape(12.dp),
        colors = if (principal) ButtonDefaults.buttonColors()
        else ButtonDefaults.buttonColors(
            containerColor = colorResource(R.color.surface),
            contentColor = colorResource(R.color.texte)
        ),
        border = if (principal) null else BorderStroke(1.dp, colorResource(R.color.bordure)),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Text(texte, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Encadre arrondi pour poser du texte lisible par-dessus le plan. */
@Composable
fun Panneau(modifier: Modifier = Modifier, contenu: @Composable RowScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(12.dp),
        color = colorResource(R.color.surface),
        border = BorderStroke(1.dp, colorResource(R.color.bordure))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = contenu
        )
    }
}

/** Fenetre par-dessus l'ecran ("Quitter ?", fiche d'une carte). */
@Composable
fun Fenetre(onFermer: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onFermer) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colorResource(R.color.surface),
            border = BorderStroke(1.dp, colorResource(R.color.bordure))
        ) {
            Column(Modifier.padding(20.dp), content = contenu)
        }
    }
}

/** Ligne "intitule ....... valeur", soulignee. */
@Composable
fun LigneInfo(intitule: String, valeur: String, enAvant: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(intitule, Modifier.weight(1f), color = colorResource(R.color.texte_discret), fontSize = 14.sp)
        Text(
            text = valeur,
            color = colorResource(if (enAvant) R.color.accent else R.color.texte),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(color = colorResource(R.color.bordure))
}

/** L'image d'une carte (res/drawable), ou son nom sur fond noir si l'image manque, avec son eclat. */
@Composable
fun VisuelCarte(carte: Carte, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(carte.image) { carte.getDrawableResId(context) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(63f / 88f) // format d'une carte a jouer
            .eclat(eclatDe(carte.quantity)) // effet a partir de 5 exemplaires (Eclats.kt)
            .background(colorResource(R.color.fond_carte))
    ) {
        if (image != 0) {
            Image(
                painter = painterResource(image),
                contentDescription = carte.nom,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = carte.nom,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                color = colorResource(R.color.texte),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        if (carte.quantity > 1) {
            Text(
                text = stringResource(R.string.quantite, carte.quantity),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(colorResource(R.color.texte))
                    .padding(horizontal = 6.dp),
                color = colorResource(R.color.fond),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

/** 245 secondes -> "4:05". */
fun formatTemps(secondes: Int) = "%d:%02d".format(secondes / 60, secondes % 60)
