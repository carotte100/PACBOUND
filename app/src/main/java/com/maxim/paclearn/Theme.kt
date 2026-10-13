package com.maxim.paclearn

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette du jeu : nuit bleutee, jaune du smiley, or des pieces, bleu du
// champ de vision. Les overlays de la carte reprennent les memes teintes.
val Night = Color(0xFF0B1426)
val NightHigh = Color(0xFF16264A)
val Panel = Color(0xE0101B30) // panneaux du HUD, la carte transparait a peine
val Smiley = Color(0xFFFFDD00)
val Gold = Color(0xFFFFB300)
val Sky = Color(0xFF409CFF)
val Alert = Color(0xFFFF5A5F)

@Composable
fun PacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Smiley,
            onPrimary = Night,
            background = Night,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White
        ),
        content = content
    )
}
