package com.maxim.paclearn

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val PillShape = RoundedCornerShape(50)
private val Outline = Color.White.copy(alpha = 0.14f)
private const val ATTRIBUTION_MS = 5_000L

/** Ce qui s'affiche par-dessus la carte : chrono, cagnotte et messages d'etat. */
@Composable
fun Hud(secondsLeft: Int, money: Int, @StringRes status: Int?) {
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimerPill(secondsLeft)
            MoneyPill(money)
        }

        // AnimatedContent garde l'ancien message affiche pendant qu'il s'efface.
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            label = "status"
        ) { message ->
            if (message != null) StatusBanner(message)
        }

        Attribution(Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Mention exigee par la licence des donnees OSM. Ses regles permettent de la
 * replier apres cinq secondes, tant qu'on peut la retrouver : ici d'un appui
 * sur le (i), qui la reaffiche pour cinq secondes.
 */
@Composable
private fun Attribution(modifier: Modifier) {
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(ATTRIBUTION_MS)
            expanded = false
        }
    }
    Row(
        modifier = modifier
            .clip(PillShape)
            .clickable { expanded = true }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Night.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = stringResource(R.string.osm_attribution),
                color = Night.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun Pill(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .background(Panel, PillShape)
            .border(1.dp, Outline, PillShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun TimerPill(secondsLeft: Int) {
    // Les 30 dernieres secondes passent en rouge pour presser le joueur.
    val color by animateColorAsState(
        if (secondsLeft <= 30) Alert else Color.White,
        label = "timerColor"
    )
    Pill {
        ClockIcon(color)
        Text(
            text = formatTime(secondsLeft),
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MoneyPill(money: Int) {
    Pill {
        CoinIcon()
        // Le montant glisse vers le haut a chaque piece ramassee.
        AnimatedContent(
            targetState = money,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "money"
        ) { value ->
            Text(
                text = "$value €",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusBanner(@StringRes message: Int) {
    val waiting = message == R.string.searching_position || message == R.string.loading_map
    Row(
        modifier = Modifier
            .background(Panel, RoundedCornerShape(16.dp))
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (waiting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Smiley,
                strokeWidth = 2.dp
            )
        }
        Text(text = stringResource(message), color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ClockIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = size.minDimension * 0.12f
        val radius = size.minDimension / 2 - stroke / 2
        drawCircle(color, radius, style = Stroke(stroke))
        drawLine(color, center, center.copy(y = center.y - radius * 0.6f), stroke, StrokeCap.Round)
        drawLine(color, center, center.copy(x = center.x + radius * 0.45f), stroke, StrokeCap.Round)
    }
}

/** La meme piece que sur la carte : de l'or avec un reflet. */
@Composable
private fun CoinIcon() {
    Canvas(Modifier.size(18.dp)) {
        val radius = size.minDimension / 2
        drawCircle(Color.White, radius)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE066), Gold),
                center = center - Offset(radius * 0.3f, radius * 0.3f),
                radius = radius
            ),
            radius * 0.78f
        )
        drawCircle(
            Color.White.copy(alpha = 0.7f),
            radius * 0.2f,
            center - Offset(radius * 0.28f, radius * 0.28f)
        )
    }
}

/** 4:05 plutot que 00:04:05 : les parties durent moins d'une heure. */
private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
