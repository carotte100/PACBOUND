package com.maxim.paclearn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Ecran d'accueil : choix de la duree de la partie avant de lancer la carte. */
class TimeSelectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.choose_duration),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        DURATIONS.forEach { minutes ->
            Button(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.minutes, minutes))
            }
        }
    }
}

private val DURATIONS = listOf(5, 10, 15, 30)

@Preview(showBackground = true)
@Composable
private fun DurationScreenPreview() {
    MaterialTheme { DurationScreen(onPick = {}) }
}
