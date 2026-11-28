package com.maxim.paclearn.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.R
import com.maxim.paclearn.controller.storage.CarteStorage
import com.maxim.paclearn.controller.storage.Catalogue
import com.maxim.paclearn.controller.storage.Reserve
import com.maxim.paclearn.controller.storage.TuileStorage
import com.maxim.paclearn.controller.storage.Triche
import com.maxim.paclearn.controller.storage.ZoneStorage
import com.maxim.paclearn.model.RecapPartie
import com.maxim.paclearn.model.VALEUR_PIECE
import com.maxim.paclearn.model.Zone
import com.maxim.paclearn.model.celluleDe
import com.maxim.paclearn.model.placerPieces
import com.maxim.paclearn.model.placerPiecesSansPlan
import com.maxim.paclearn.view.CalqueJoueur
import com.maxim.paclearn.view.CalquePieces
import com.maxim.paclearn.view.CalqueRues
import com.maxim.paclearn.view.EcranBoutique
import com.maxim.paclearn.view.EcranRecap
import com.maxim.paclearn.view.FenetreQuitter
import com.maxim.paclearn.view.Hud
import com.maxim.paclearn.view.PacTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File

/** Une partie : GPS, chrono, pieces ramassees. Boutique, "Quitter ?" et recap s'affichent par-dessus. */
class JeuActivity : ComponentActivity() {

    private lateinit var plan: MapView
    private lateinit var calqueJoueur: CalqueJoueur
    private lateinit var boussole: Boussole
    private var calqueRues: CalqueRues? = null
    private var calquePieces: CalquePieces? = null // null tant que la zone n'est pas chargee

    private var centreZone: GeoPoint? = null
    private var chargement: Job? = null
    private val piecesRamassees = HashSet<Long>() // cellules (celluleDe) des pieces deja prises

    private val gps by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var dernierPoint: Location? = null
    private val ecouteGps = object : LocationListener {
        override fun onLocationChanged(point: Location) = accepterPoint(point)
        @Deprecated("Exige par LocationListener avant l'API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // Etats Compose : l'ecran se redessine quand ils changent.
    private var argent by mutableStateOf(0)
    private var tempsRestant by mutableStateOf(0) // secondes
    private var message by mutableStateOf<Int?>(R.string.recherche_position)
    private var boutiqueOuverte by mutableStateOf(false)
    private var quitterDemande by mutableStateOf(false)
    private var recap by mutableStateOf<RecapPartie?>(null) // non nul : partie finie
    private var dureeTotale = 0
    private var argentDepart = 0
    private var nbPieces = 0
    private var chrono: Job? = null

    private val zones by lazy { ZoneStorage(this) }
    private val tuiles by lazy { TuileStorage(this) }

    private val boutique by lazy {
        Boutique(Catalogue.load(this), Reserve(this), CarteStorage(this), payer = ::payer)
    }

    private val demandePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reponses ->
        if (reponses.values.any { it }) suivreGps()
        else message = R.string.localisation_refusee
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
            SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        )
        preparerPlan()

        dureeTotale = intent.getIntExtra(EXTRA_MINUTES, 5) * 60
        tempsRestant = dureeTotale
        argentDepart = Triche(this).prendreBonus() // 0 sauf si le menu de triche en a donne
        argent = argentDepart

        setContent {
            PacTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { plan }, modifier = Modifier.fillMaxSize())
                    Hud(
                        tempsRestant = tempsRestant,
                        argent = argent,
                        message = message,
                        onBoutique = { boutiqueOuverte = true },
                        onQuitter = { quitterDemande = true }
                    )
                    BackHandler(enabled = recap == null) { quitterDemande = true }

                    if (boutiqueOuverte) {
                        EcranBoutique(boutique, argent, onFermer = { boutiqueOuverte = false })
                    }
                    if (quitterDemande) {
                        FenetreQuitter(
                            argent = argent,
                            nbAmeliorations = boutique.actives.size,
                            onRester = { quitterDemande = false },
                            onQuitter = { finirPartie(tempsEcoule = false) }
                        )
                    }
                    recap?.let { EcranRecap(it, onTerminer = ::finish) }
                }
            }
        }

        if (aLaPermission()) suivreGps()
        else demandePermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )

        chrono = lifecycleScope.launch {
            while (tempsRestant > 0) {
                delay(1000)
                tempsRestant--
            }
            finirPartie(tempsEcoule = true)
        }
    }

    private fun preparerPlan() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        plan = MapView(this).apply {
            // Pas de tuiles : CalqueRues dessine les rues sur ce fond.
            overlayManager.tilesOverlay.isEnabled = false
            setUseDataConnection(false)
            // Pas setBackgroundColor : osmdroid le detourne et le plan restait transparent.
            background = ColorDrawable(ContextCompat.getColor(context, R.color.plan_sol))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // Pas de dezoom : on verrait le bord de la zone chargee.
            setMinZoomLevel(ZOOM)
            setMaxZoomLevel(ZOOM_MAX)
            controller.setZoom(ZOOM)
        }
        calqueJoueur = CalqueJoueur(this, RAYON_RAMASSAGE_M)
        plan.overlays.add(calqueJoueur)
        boussole = Boussole(this) { cap ->
            calqueJoueur.cap = cap
            plan.postInvalidate()
        }
    }

    /** Appele par la boutique a chaque achat. */
    private fun payer(montant: Int): Boolean {
        if (recap != null || argent < montant) return false
        argent -= montant
        return true
    }

    private fun finirPartie(tempsEcoule: Boolean) {
        if (recap != null) return
        chrono?.cancel()
        chargement?.cancel()
        gps.removeUpdates(ecouteGps)
        boutiqueOuverte = false
        quitterDemande = false
        recap = RecapPartie(
            secondesJouees = dureeTotale - tempsRestant,
            pieces = nbPieces,
            argentRestant = argent,
            argentDepart = argentDepart,
            cartes = boutique.cartesAchetees.toList(),
            ameliorations = boutique.actives.toList(),
            tempsEcoule = tempsEcoule
        )
    }

    // ---- GPS ----

    private fun aLaPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun suivreGps() {
        // GPS : precis mais lent. Reseau : rapide mais imprecis.
        for (source in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (source in gps.allProviders) gps.requestLocationUpdates(source, 1_000L, 0f, ecouteGps)
        }
        utiliserDernierePosition()
        lifecycleScope.launch {
            while (centreZone == null) {
                delay(REESSAI_MS)
                utiliserDernierePosition()
            }
        }
    }

    /** Le premier point GPS peut tarder : on demarre sur la derniere position connue. */
    @SuppressLint("MissingPermission")
    private fun utiliserDernierePosition() {
        val derniere = gps.allProviders
            .mapNotNull { gps.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (derniere != null) {
            accepterPoint(derniere)
            return
        }
        val gpsAllume = gps.allProviders.any { gps.isProviderEnabled(it) }
        message = if (gpsAllume) R.string.recherche_position else R.string.localisation_desactivee
    }

    private fun accepterPoint(point: Location) {
        if (!estPlausible(point)) return
        dernierPoint = point
        joueurDeplace(GeoPoint(point.latitude, point.longitude))
    }

    /** Ecarte les points bien moins precis, le tremblement (< 3 m) et les sauts (> 8 m/s). */
    private fun estPlausible(nouveau: Location): Boolean {
        val ancien = dernierPoint ?: return true
        val erreurNouveau = erreurDe(nouveau)
        val erreurAncien = erreurDe(ancien)
        if (erreurNouveau < erreurAncien / 2f) return true

        val secondes = (nouveau.elapsedRealtimeNanos - ancien.elapsedRealtimeNanos).coerceAtLeast(0) / 1e9
        // Un vieux point perd sa precision (le joueur a pu marcher), sinon il bloquerait les suivants.
        val erreurAncienMaintenant = erreurAncien + secondes * VITESSE_MARCHE_MS
        if (erreurNouveau > erreurAncienMaintenant * 2) return false

        val distance = nouveau.distanceTo(ancien)
        if (distance < 3f) return false
        if (secondes > 0 && distance / secondes > VITESSE_MAX_MS) return false
        return true
    }

    private fun erreurDe(point: Location) = if (point.hasAccuracy()) point.accuracy else 100f

    // ---- Le jeu ----

    private fun joueurDeplace(position: GeoPoint) {
        if (recap != null) return
        if (message == R.string.recherche_position || message == R.string.localisation_desactivee) message = null
        calqueJoueur.position = position
        plan.controller.animateTo(position)
        plan.invalidate()

        ramasserPieces(position)

        val centre = centreZone
        if (centre == null || centre.distanceToAsDouble(position) > RECHARGER_APRES_M) chargerZone(position)
    }

    private fun ramasserPieces(position: GeoPoint) {
        val calque = calquePieces ?: return
        val prises = calque.ramasser(position, RAYON_RAMASSAGE_M)
        if (prises.isEmpty()) return
        argent += prises.size * VALEUR_PIECE
        nbPieces += prises.size
        prises.forEach { piecesRamassees.add(celluleDe(it)) } // pour qu'elles ne reapparaissent pas
        plan.invalidate()
    }

    /**
     * Charge les rues autour de [centre] (enregistrees, sinon reseau, sinon secours), puis
     * dessine le plan et pose les pieces. Sans aucune rue : mode sans plan, et on reessaie.
     */
    private fun chargerZone(centre: GeoPoint) {
        centreZone = centre
        chargement?.cancel()
        if (calquePieces == null) message = R.string.chargement_plan
        chargement = lifecycleScope.launch {
            while (true) {
                val rues = withContext(Dispatchers.IO) { chargerRues(zones, tuiles, centre, DEMI_COTE_ZONE_M, packageName) }
                val zone = rues?.zone ?: Zone(emptyList(), emptyList())
                val pieces = withContext(Dispatchers.Default) {
                    if (rues != null) placerPieces(zone, centre, DEMI_COTE_ZONE_M, ESPACEMENT_PIECES_M)
                    else placerPiecesSansPlan(centre, DEMI_COTE_ZONE_M, ESPACEMENT_PIECES_M)
                }
                afficherZone(zone, centre, pieces)
                message = when (rues?.origine) {
                    null -> R.string.hors_ligne_sans_plan
                    Origine.SECOURS -> R.string.hors_ligne_plan_enregistre
                    else -> null
                }
                // Rues a jour : fini. Sinon on reessaie plus tard, au cas ou le reseau revient.
                if (rues != null && rues.origine != Origine.SECOURS) return@launch
                delay(REESSAI_HORS_LIGNE_MS)
            }
        }
    }

    /** Remplace l'ancienne zone (restee affichee pendant le chargement) par la nouvelle. */
    private fun afficherZone(zone: Zone, centre: GeoPoint, pieces: List<GeoPoint>) {
        calqueRues?.let { plan.overlays.remove(it) }
        calquePieces?.let { plan.overlays.remove(it) }
        calqueRues = CalqueRues(zone, centre, this).also { plan.overlays.add(0, it) }
        calquePieces = CalquePieces(pieces.filterNot { celluleDe(it) in piecesRamassees }, this)
            .also { plan.overlays.add(1, it) }
        plan.invalidate()
    }

    // ---- Cycle de vie ----

    override fun onResume() {
        super.onResume()
        plan.onResume()
        boussole.demarrer()
    }

    override fun onPause() {
        boussole.arreter()
        plan.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        gps.removeUpdates(ecouteGps)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MINUTES = "minutes"

        private const val ZOOM = 19.0
        private const val ZOOM_MAX = 21.0
        private const val DEMI_COTE_ZONE_M = 400.0 // zone chargee : un carre de 800 m
        private const val RECHARGER_APRES_M = 200.0
        private const val ESPACEMENT_PIECES_M = 90.0
        private const val RAYON_RAMASSAGE_M = 15.0 // le GPS se trompe de 5 a 15 m
        private const val REESSAI_MS = 10_000L
        private const val REESSAI_HORS_LIGNE_MS = 30_000L
        private const val VITESSE_MAX_MS = 8.0
        private const val VITESSE_MARCHE_MS = 1.5
    }
}
