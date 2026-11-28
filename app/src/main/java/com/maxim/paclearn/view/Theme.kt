package com.maxim.paclearn.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.maxim.paclearn.R

/** Applique les couleurs de res/values/colors.xml a tous les composants Material. */
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
