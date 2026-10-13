package com.maxim.paclearn

import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ecran d'accueil : choix de la duree de la partie avant de lancer la carte. */
class TimeSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT)
        )
        setContent {
            PacTheme {
                DurationScreen(onPick = ::startRun)
            }
        }
    }

    private fun startRun(minutes: Int) {
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(EXTRA_MINUTES, minutes)
        )
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
        SmileyLogo(Modifier.size(120.dp))
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
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$minutes",
                color = Smiley,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = stringResource(R.string.minutes_label),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

/** Le joueur tel qu'il apparait sur la carte : smiley et halo du champ de vision. */
@Composable
private fun SmileyLogo(modifier: Modifier) {
    Canvas(modifier) {
        val glow = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Sky.copy(alpha = 0.5f), Color.Transparent), center, glow))

        // Memes proportions que PlayerOverlay.drawSmiley.
        val face = glow * 0.55f
        drawCircle(Color.White, face * 1.18f)
        drawCircle(Smiley, face)

        val eyeSize = Size(face * 0.24f, face * 0.40f)
        val eyeY = center.y - face * 0.22f - eyeSize.height / 2
        for (dx in listOf(-face * 0.34f, face * 0.34f)) {
            drawOval(Color.Black, Offset(center.x + dx - eyeSize.width / 2, eyeY), eyeSize)
        }
        drawArc(
            color = Color.Black,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - face * 0.55f, center.y - face * 0.35f),
            size = Size(face * 1.1f, face * 0.9f),
            style = Stroke(face * 0.16f, cap = StrokeCap.Round)
        )
    }
}

private val DURATIONS = listOf(5, 10, 15, 30)

@Preview
@Composable
private fun DurationScreenPreview() {
    PacTheme { DurationScreen(onPick = {}) }
}
