package com.maxim.paclearn.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color.TRANSPARENT
import android.location.LocationManager
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.R
import com.maxim.paclearn.controller.storage.CarteStorage
import com.maxim.paclearn.controller.storage.Catalogue
import com.maxim.paclearn.controller.storage.TuileStorage
import com.maxim.paclearn.controller.storage.Triche
import com.maxim.paclearn.view.EcranAccueil
import com.maxim.paclearn.view.FenetreTriche
import com.maxim.paclearn.view.PacTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

/** Ecran d'accueil : lance une partie, ouvre la collection, enregistre le quartier pour jouer hors ligne. */
class AccueilActivity : ComponentActivity() {

    private val tuiles by lazy { TuileStorage(this) }
    private var etatQuartier by mutableStateOf("")
    private var quartierEnCours by mutableStateOf(false)
    private val triche by lazy { Triche(this) }
    private var tricheOuverte by mutableStateOf(false)
    private var bonusArgent by mutableStateOf(0)
    private var messageTriche by mutableStateOf("")

    private val demandePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reponses ->
        if (reponses.values.any { it }) lancerTelechargementQuartier()
        else etatQuartier = getString(R.string.localisation_refusee)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        etatQuartier = if (tuiles.nombre() > 0) quartierEnregistre()
        else getString(R.string.quartier_absent, COTE_QUARTIER_KM.toInt())
        setContent {
            PacTheme {
                EcranAccueil(
                    onJouer = ::lancerPartie,
                    onCollection = { startActivity(Intent(this, CollectionActivity::class.java)) },
                    coteQuartierKm = COTE_QUARTIER_KM.toInt(),
                    etatQuartier = etatQuartier,
                    quartierEnCours = quartierEnCours,
                    onQuartier = ::demanderQuartier,
                    onTriche = {
                        bonusArgent = triche.bonusArgent
                        messageTriche = ""
                        tricheOuverte = true
                    }
                )
                if (tricheOuverte) {
                    FenetreTriche(
                        bonusArgent = bonusArgent,
                        message = messageTriche,
                        onArgent = {
                            triche.bonusArgent += 100
                            bonusArgent = triche.bonusArgent
                        },
                        onCarte = { messageTriche = donnerCartes(toutes = false) },
                        onToutesLesCartes = { messageTriche = donnerCartes(toutes = true) },
                        onViderCollection = {
                            val collection = CarteStorage(this)
                            collection.getCollection().forEach { collection.removeOrDecrement(it, it.quantity) }
                            messageTriche = "Collection vidée"
                        },
                        onFermer = { tricheOuverte = false }
                    )
                }
            }
        }
    }

    private fun lancerPartie(minutes: Int) {
        startActivity(Intent(this, JeuActivity::class.java).putExtra(JeuActivity.EXTRA_MINUTES, minutes))
    }

    private fun demanderQuartier() {
        val permis = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (permis) lancerTelechargementQuartier()
        else demandePermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    /** Enregistre le carre de [COTE_QUARTIER_KM] km autour de la derniere position connue. A faire en Wi-Fi. */
    @SuppressLint("MissingPermission")
    private fun lancerTelechargementQuartier() {
        if (quartierEnCours) return
        val gps = getSystemService(LOCATION_SERVICE) as LocationManager
        val position = gps.allProviders
            .mapNotNull { gps.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (position == null) {
            etatQuartier = getString(R.string.quartier_sans_position)
            return
        }
        quartierEnCours = true
        etatQuartier = getString(R.string.quartier_en_cours, 0, 1)
        val centre = GeoPoint(position.latitude, position.longitude)
        lifecycleScope.launch {
            val echecs = withContext(Dispatchers.IO) {
                tuiles.vider() // un seul quartier : le nouveau remplace l'ancien
                telechargerQuartier(tuiles, centre, COTE_QUARTIER_KM, packageName) { fait, total ->
                    etatQuartier = getString(R.string.quartier_en_cours, fait, total)
                }
            }
            etatQuartier = when {
                tuiles.nombre() == 0 -> getString(R.string.quartier_echec)
                echecs > 0 -> getString(R.string.quartier_partiel, echecs)
                else -> quartierEnregistre()
            }
            quartierEnCours = false
        }
    }

    /** Triche : une carte au hasard, ou une de chaque, ajoutee a la collection. */
    private fun donnerCartes(toutes: Boolean): String {
        val catalogue = Catalogue.load(this).cartes
        if (catalogue.isEmpty()) return "Catalogue vide"
        val collection = CarteStorage(this)
        val donnees = if (toutes) catalogue else listOf(catalogue.random())
        donnees.forEach { collection.addOrIncrement(it) }
        return if (toutes) "+1 de chaque carte (${donnees.size})" else "+1 ${donnees.first().nom}"
    }

    private fun quartierEnregistre() =
        getString(R.string.quartier_enregistre, Formatter.formatShortFileSize(this, tuiles.octets()))

    private companion object {
        const val COTE_QUARTIER_KM = 10.0 // environ 5 Mo en ville, une minute ou deux en Wi-Fi
    }
}
