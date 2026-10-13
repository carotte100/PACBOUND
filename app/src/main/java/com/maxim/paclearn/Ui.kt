package com.maxim.paclearn

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// L'interface Compose : la palette, l'ecran d'accueil et le HUD de la partie.

// Palette du jeu : nuit bleutee, jaune du smiley, bleu du champ de vision.
// Les calques de la carte (Map.kt) reprennent les memes teintes.
val Night = Color(0xFF0B1426)
val NightHigh = Color(0xFF16264A)
val Panel = Color(0xE0101B30) // panneaux du HUD, la carte transparait a peine
val Smiley = Color(0xFFFFDD00)
val Sky = Color(0xFF409CFF)
val Alert = Color(0xFFFF5A5F)

private val PillShape = RoundedCornerShape(50)
private val Outline = Color.White.copy(alpha = 0.14f)
private val DURATIONS = listOf(5, 10, 15, 30)
private const val ATTRIBUTION_MS = 5_000L

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

/** Ecran d'accueil : choix de la duree de la partie avant de lancer la carte. */
class TimeSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT)
        )
        setContent {
            PacTheme { DurationScreen(onPick = ::startRun) }
        }
    }

    private fun startRun(minutes: Int) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_MINUTES, minutes))
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"
    }
}

@Composable
private fun DurationScreen(onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightHigh, Night)))
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Le joueur tel qu'il apparait sur la carte : smiley et halo du champ de vision.
        Canvas(Modifier.size(120.dp)) {
            val glow = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Sky.copy(alpha = 0.5f), Color.Transparent), center, glow))
            drawIntoCanvas { drawSmiley(it.nativeCanvas, center.x, center.y, glow * 0.55f) }
        }
        Text(
            text = stringResource(R.string.app_name),
            color = Smiley,
            fontSize = 44.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = stringResource(R.string.tagline),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(48.dp))

        Text(
            text = stringResource(R.string.choose_duration).uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        // Deux lignes de deux : les durees se choisissent d'un pouce.
        DURATIONS.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { minutes ->
                    DurationCard(minutes, Modifier.weight(1f)) { onPick(minutes) }
                }
            }
        }
    }
}

@Composable
private fun DurationCard(minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.5f),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$minutes", color = Smiley, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(
                text = stringResource(R.string.minutes_label),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

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
            if (message != null) {
                Pill {
                    if (message == R.string.searching_position || message == R.string.loading_map) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Smiley, strokeWidth = 2.dp)
                    }
                    Text(text = stringResource(message), color = Color.White, fontSize = 14.sp)
                }
            }
        }

        Attribution(Modifier.align(Alignment.BottomStart))
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
    val color by animateColorAsState(if (secondsLeft <= 30) Alert else Color.White, label = "timerColor")
    Pill {
        Canvas(Modifier.size(16.dp)) {
            val stroke = size.minDimension * 0.12f
            val radius = size.minDimension / 2 - stroke / 2
            drawCircle(color, radius, style = Stroke(stroke))
            drawLine(color, center, center.copy(y = center.y - radius * 0.6f), stroke, StrokeCap.Round)
            drawLine(color, center, center.copy(x = center.x + radius * 0.45f), stroke, StrokeCap.Round)
        }
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
    // La meme piece que sur la carte, dessinee une seule fois.
    val density = LocalDensity.current.density
    val coin = remember(density) { drawCoin(5.6f * density).asImageBitmap() }
    Pill {
        Image(coin, contentDescription = null, modifier = Modifier.size(18.dp))
        // Le montant glisse vers le haut a chaque piece ramassee.
        AnimatedContent(
            targetState = money,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "money"
        ) { value ->
            Text(text = "$value €", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
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

/** 4:05 plutot que 00:04:05 : les parties durent moins d'une heure. */
private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
