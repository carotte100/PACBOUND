package com.maxim.paclearn.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxim.paclearn.R
import com.maxim.paclearn.model.Eclat

/**
 * Arrondit la carte et lui ajoute l'effet de son [eclat] :
 * - BRILLANTE : liseré doré avec un reflet qui en fait le tour, léger halo doré ;
 * - HOLOGRAPHIQUE : liseré arc-en-ciel qui défile et éclat de lumière en diagonale ;
 * - LEGENDAIRE : comme holographique, avec un halo coloré qui pulse.
 */
fun Modifier.eclat(eclat: Eclat, coin: Dp = 8.dp): Modifier =
    if (eclat == Eclat.AUCUN) clip(RoundedCornerShape(coin))
    else composed {
        val or = colorResource(R.color.eclat_or)
        val orClair = colorResource(R.color.eclat_or_clair)
        val holo = listOf(R.color.holo_1, R.color.holo_2, R.color.holo_3, R.color.holo_4, R.color.holo_5)
            .map { colorResource(it) }

        val transition = rememberInfiniteTransition(label = "eclat")
        val phase by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "phase"
        )
        val pulsation by transition.animateFloat(
            initialValue = 0.3f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "pulsation"
        )

        val brillante = eclat == Eclat.BRILLANTE
        val couleursBord = if (brillante) listOf(or, orClair, or) else holo + holo.first()
        val couleurHalo = if (brillante) or else holo[(phase * holo.size).toInt().coerceIn(0, holo.size - 1)]
        val forceHalo = if (eclat == Eclat.LEGENDAIRE) pulsation else 0.35f
        val epaisseur = if (brillante) 2.dp else 3.dp

        this
            // Le halo, dessine autour de la carte (avant l'arrondi, sinon il serait coupe).
            .drawBehind {
                val pas = 2.dp.toPx()
                for (i in 1..4) {
                    val ecart = i * pas
                    drawRoundRect(
                        color = couleurHalo.copy(alpha = forceHalo * (1f - i / 5f)),
                        topLeft = Offset(-ecart, -ecart),
                        size = Size(size.width + 2 * ecart, size.height + 2 * ecart),
                        cornerRadius = CornerRadius(coin.toPx() + ecart),
                        style = Stroke(pas)
                    )
                }
            }
            .clip(RoundedCornerShape(coin))
            // Par-dessus l'image : l'eclat de lumiere (holo et legendaire), puis le liseré.
            .drawWithContent {
                drawContent()
                val w = size.width
                val h = size.height
                if (!brillante) {
                    val x = (phase * 3f - 1f) * w // visible un tiers du temps, puis une pause
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            start = Offset(x, 0f), end = Offset(x + w * 0.6f, h)
                        )
                    )
                }
                val decalage = phase * 2 * w
                drawRoundRect(
                    brush = Brush.linearGradient(
                        couleursBord,
                        start = Offset(decalage - w, 0f), end = Offset(decalage, h),
                        tileMode = TileMode.Mirror
                    ),
                    cornerRadius = CornerRadius(coin.toPx()),
                    style = Stroke(epaisseur.toPx() * 2) // la moitie exterieure est coupee par l'arrondi
                )
            }
    }
