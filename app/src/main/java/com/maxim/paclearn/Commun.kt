package com.maxim.paclearn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
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
import com.maxim.paclearn.model.Carte

// Ce qui sert a tous les ecrans : le theme, et les petits elements d'interface
// (titre, bouton, panneau, fenetre, ligne d'info, visuel d'une carte).
// Toutes les couleurs viennent de res/values/colors.xml, tous les textes de strings.xml.

/**
 * Donne les couleurs de colors.xml aux composants Material : grace a lui, un
 * Text est blanc, un Button orange, une Surface bleue, sans rien preciser.
 */
@Composable
fun PacTheme(contenu: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(R.color.accent),
            onPrimary = colorResource(R.color.texte),
            background = colorResource(R.color.fond),
            onBackground = colorResource(R.color.texte),
            surface = colorResource(R.color.fond),
            onSurface = colorResource(R.color.texte),
            outline = colorResource(R.color.bordure)
        ),
        content = contenu
    )
}

/** Un titre en majuscules et en serif, comme sur les cartes Riftbound. */
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

/**
 * Le bouton du jeu, rectangulaire. [principal] : rempli d'orange, pour l'action
 * importante (acheter, continuer). Sinon : un simple contour.
 */
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
        modifier = modifier,
        enabled = actif,
        shape = RectangleShape,
        colors = if (principal) ButtonDefaults.buttonColors()
        else ButtonDefaults.buttonColors(
            containerColor = colorResource(R.color.fond),
            contentColor = colorResource(R.color.texte)
        ),
        border = if (principal) null else BorderStroke(1.dp, colorResource(R.color.bordure)),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(texte, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Un encadre au fond bleu, pour poser du texte lisible par-dessus le plan. */
@Composable
fun Panneau(modifier: Modifier = Modifier, contenu: @Composable RowScope.() -> Unit) {
    Surface(modifier, border = BorderStroke(1.dp, colorResource(R.color.bordure))) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = contenu
        )
    }
}

/** Une fenetre par-dessus l'ecran : "Quitter ?", fiche d'une carte. */
@Composable
fun Fenetre(onFermer: () -> Unit, contenu: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onFermer) {
        Surface(border = BorderStroke(1.dp, colorResource(R.color.bordure))) {
            Column(Modifier.padding(20.dp), content = contenu)
        }
    }
}

/** Une ligne "intitule ....... valeur", soulignee. */
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

/** L'image d'une carte (res/drawable), ou son nom sur fond noir si l'image manque. */
@Composable
fun VisuelCarte(carte: Carte, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = remember(carte.image) { carte.getDrawableResId(context) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(63f / 88f) // le format d'une carte a jouer
            .clip(RoundedCornerShape(8.dp))
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