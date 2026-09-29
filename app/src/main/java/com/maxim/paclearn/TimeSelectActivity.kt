package com.maxim.paclearn

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.maxim.paclearn.databinding.ActivityTimeSelectBinding

/** Ecran d'accueil : choix de la duree de la partie avant de lancer la carte. */
class TimeSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityTimeSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.duration5.setOnClickListener { startRun(5) }
        binding.duration10.setOnClickListener { startRun(10) }
        binding.duration15.setOnClickListener { startRun(15) }
        binding.duration30.setOnClickListener { startRun(30) }
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
