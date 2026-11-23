package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.storage.CarteStorage
import com.maxim.paclearn.storage.Catalogue
import com.maxim.paclearn.storage.Reserve
import kotlinx.coroutines.CancellationException
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
import kotlin.math.abs

/** Ce que rapporte une piece ramassee, en euros. Les prix de la boutique sont dans Boutique.kt. */
const val VALEUR_PIECE = 2

/**
 * L'ecran de jeu : le plan, le joueur qui s'y deplace, les pieces a ramasser et
 * le chrono. Par-dessus s'affichent la boutique, la fenetre "Quitter ?" et le recap.
 */
class JeuActivity : ComponentActivity() {

    // Le plan et ses calques (Plan.kt).
    private lateinit var plan: MapView
    private lateinit var calqueJoueur: CalqueJoueur
    private lateinit var boussole: Boussole
    private var calqueRues: CalqueRues? = null
    private var calquePieces: CalquePieces? = null // null tant que la zone n'est pas chargee

    // La zone chargee autour du joueur (Rues.kt).
    private var centreZone: GeoPoint? = null
    private var chargement: Job? = null
    private val piecesRamassees = HashSet<Long>() // les cellules (celluleDe) des pieces deja prises

    // Le GPS.
    private val gps by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var dernierPoint: Location? = null
    private val ecouteGps = object : LocationListener {
        override fun onLocationChanged(point: Location) = accepterPoint(point)
        @Deprecated("Exige par LocationListener avant l'API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // La partie. Les etats Compose (mutableStateOf) redessinent l'ecran quand ils changent.
    private var argent by mutableStateOf(0)
    private var tempsRestant by mutableStateOf(0) // en secondes
    private var message by mutableStateOf<Int?>(R.string.recherche_position)
    private var boutiqueOuverte by mutableStateOf(false)
    private var quitterDemande by mutableStateOf(false)
    private var recap by mutableStateOf<RecapPartie?>(null) // non nul : la partie est finie
    private var dureeTotale = 0
    private var nbPieces = 0
    private var chrono: Job? = null

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
        // Le plan passe sous les barres systeme, aux icones sombres sur le vert.
        enableEdgeToEdge(
            SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
            SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        )
        preparerPlan()

        dureeTotale = intent.getIntExtra(EXTRA_MINUTES, 5) * 60
        tempsRestant = dureeTotale

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
                    // Le bouton retour ne quitte pas d'un coup : on rappelle ce qui sera perdu.
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

        // Le chrono : une coroutine, qui attend sans bloquer l'ecran.
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
            // Pas de tuiles : CalqueRues dessine les rues sur ce fond vert.
            overlayManager.tilesOverlay.isEnabled = false
            setUseDataConnection(false)
            // Surtout pas setBackgroundColor : osmdroid le detourne vers la couleur
            // d'attente des tuiles, et le plan restait transparent.
            background = ColorDrawable(ContextCompat.getColor(context, R.color.plan_sol))
            setMultiTouchControls(true)
            // Le pincement suffit : pas de boutons +/-.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // Pas de dezoom : plus loin, on verrait le bord de la zone chargee.
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

    /** Retire [montant] euros si le joueur les a. La boutique passe par ici a chaque achat. */
    private fun payer(montant: Int): Boolean {
        if (recap != null || argent < montant) return false
        argent -= montant
        return true
    }

    /** Arrete tout (chrono, GPS, chargements) et affiche le recap. */
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
        // Le GPS est precis mais lent a demarrer, le reseau (Wi-Fi) rapide mais imprecis.
        for (source in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (source in gps.allProviders) gps.requestLocationUpdates(source, 1_000L, 0f, ecouteGps)
        }
        utiliserDernierePosition()
        // Sans premier point, ni joueur ni pieces : on relit regulierement la derniere position.
        lifecycleScope.launch {
            while (centreZone == null) {
                delay(REESSAI_MS)
                utiliserDernierePosition()
            }
        }
    }

    /**
     * Un premier point GPS peut prendre des minutes en interieur : on demarre
     * sur la derniere position connue du telephone.
     */
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

    /**
     * Filtre les positions absurdes, en comparant chaque point a celui en cours :
     * 1. bien plus precis que lui : accepte ;
     * 2. bien moins precis : refuse (un point Wi-Fi a 300 m pres teleporterait le joueur) ;
     * 3. a moins de 3 m : refuse, c'est le GPS qui tremble ;
     * 4. a plus de 8 m/s : refuse, un pieton ne se teleporte pas.
     */
    private fun estPlausible(nouveau: Location): Boolean {
        val ancien = dernierPoint ?: return true
        val erreurNouveau = erreurDe(nouveau)
        val erreurAncien = erreurDe(ancien)
        if (erreurNouveau < erreurAncien / 2f) return true

        // Horloge du telephone, insensible aux changements d'heure.
        val secondes = (nouveau.elapsedRealtimeNanos - ancien.elapsedRealtimeNanos).coerceAtLeast(0) / 1e9
        // Un point perd sa precision en vieillissant, puisque le joueur a pu marcher
        // depuis : sans cela, un vieux point tres precis bloquait tous les suivants.
        val erreurAncienMaintenant = erreurAncien + secondes * VITESSE_MARCHE_MS
        if (erreurNouveau > erreurAncienMaintenant * 2) return false

        val distance = nouveau.distanceTo(ancien)
        if (distance < 3f) return false
        if (secondes > 0 && distance / secondes > VITESSE_MAX_MS) return false
        return true
    }

    // Un point sans precision annoncee est suppose juste a 100 m pres.
    private fun erreurDe(point: Location) = if (point.hasAccuracy()) point.accuracy else 100f

    // ---- Le jeu ----

    private fun joueurDeplace(position: GeoPoint) {
        if (recap != null) return // partie finie : plus rien a ramasser
        // Seuls les messages de recherche tombent : celui du chargement reste.
        if (message == R.string.recherche_position || message == R.string.localisation_desactivee) message = null
        calqueJoueur.position = position
        plan.controller.animateTo(position)
        plan.invalidate()

        ramasserPieces(position)

        val centre = centreZone
        if (centre == null || centre.distanceToAsDouble(position) > RECHARGER_APRES_M) chargerZone(position)
    }

    private fun ramasserPieces(position: GeoPoint) {
        val calque = calquePieces ?: return // zone pas encore chargee : rien a ramasser
        val prises = calque.ramasser(position, RAYON_RAMASSAGE_M)
        if (prises.isEmpty()) return
        argent += prises.size * VALEUR_PIECE
        nbPieces += prises.size
        // Retenues pour qu'elles ne reapparaissent pas au prochain chargement de la zone.
        prises.forEach { piecesRamassees.add(celluleDe(it)) }
        plan.invalidate()
    }

    /** Telecharge les rues autour de [centre], puis dessine le plan et pose les pieces. */
    private fun chargerZone(centre: GeoPoint) {
        centreZone = centre
        chargement?.cancel()
        if (calquePieces == null) message = R.string.chargement_plan
        chargement = lifecycleScope.launch {
            // Les serveurs publics tombent souvent : on reessaie au lieu d'abandonner.
            while (true) {
                try {
                    // Dispatchers.IO : le telechargement se fait en arriere-plan.
                    val zone = withContext(Dispatchers.IO) { telechargerZone(centre, DEMI_COTE_ZONE_M, packageName) }
                    // Dispatchers.Default : le placement des pieces aussi, c'est un gros calcul.
                    val pieces = withContext(Dispatchers.Default) {
                        placerPieces(zone, centre, DEMI_COTE_ZONE_M, ESPACEMENT_PIECES_M)
                    }
                    // L'ancienne zone reste affichee jusqu'ici : pas de plan vide pendant le chargement.
                    calqueRues?.let { plan.overlays.remove(it) }
                    calquePieces?.let { plan.overlays.remove(it) }
                    calqueRues = CalqueRues(zone, centre, this@JeuActivity)
                        .also { plan.overlays.add(0, it) }
                    calquePieces = CalquePieces(pieces.filterNot { celluleDe(it) in piecesRamassees }, this@JeuActivity)
                        .also { plan.overlays.add(1, it) }
                    plan.invalidate()
                    message = null
                    return@launch
                } catch (e: CancellationException) {
                    throw e // remplace par un chargement plus proche du joueur : pas une erreur
                } catch (e: Exception) {
                    message = R.string.rues_indisponibles
                    delay(REESSAI_MS)
                }
            }
        }
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

        private const val ZOOM = 19.0 // au 18, la zone de ramassage disparait sous le smiley
        private const val ZOOM_MAX = 21.0
        private const val DEMI_COTE_ZONE_M = 400.0 // la zone chargee : un carre de 800 m
        private const val RECHARGER_APRES_M = 200.0
        private const val ESPACEMENT_PIECES_M = 90.0
        private const val RAYON_RAMASSAGE_M = 15.0 // le GPS d'un telephone se trompe de 5 a 15 m
        private const val REESSAI_MS = 10_000L
        private const val VITESSE_MAX_MS = 8.0 // un pieton ne sprinte pas plus vite
        private const val VITESSE_MARCHE_MS = 1.5
    }
}

/** Ce qui s'affiche par-dessus le plan : chrono, argent, boutons et messages. */
@Composable
private fun Hud(
    tempsRestant: Int,
    argent: Int,
    @StringRes message: Int?,
    onBoutique: () -> Unit,
    onQuitter: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panneau {
                    // Les 30 dernieres secondes passent en rouge, pour presser le joueur.
                    Text(
                        text = formatTemps(tempsRestant),
                        color = colorResource(if (tempsRestant <= 30) R.color.alerte else R.color.texte),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Bouton(stringResource(R.string.quitter), onQuitter)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Panneau {
                    Text(
                        text = stringResource(R.string.euros, argent),
                        color = colorResource(R.color.accent),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Bouton(stringResource(R.string.boutique), onBoutique, principal = true)
            }
        }

        if (message != null) {
            Panneau(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                if (message == R.string.recherche_position || message == R.string.chargement_plan) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(message), fontSize = 14.sp)
            }
        }

        MentionOsm(Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Mention exigee par la licence des donnees OpenStreetMap. Ses regles permettent
 * de la replier apres 5 secondes, tant qu'on peut la retrouver : d'un appui sur ⓘ.
 */
@Composable
private fun MentionOsm(modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(5_000)
            visible = false
        }
    }
    Text(
        text = stringResource(if (visible) R.string.attribution_osm else R.string.info),
        modifier = modifier
            .clickable { visible = true }
            .padding(4.dp),
        color = colorResource(R.color.fond),
        fontSize = 12.sp
    )
}

/**
 * La boussole : le cap du telephone en degres (0 = nord), pour orienter le regard
 * du joueur. Suppose l'ecran en portrait.
 */
private class Boussole(context: Context, private val onCap: (Float) -> Unit) : SensorEventListener {

    private val capteurs = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = capteurs.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrice = FloatArray(9)
    private val orientation = FloatArray(3)
    private var capLisse = Float.NaN
    private var capAnnonce = Float.NaN

    fun demarrer() {
        rotation?.let { capteurs.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun arreter() = capteurs.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matrice, event.values)
        SensorManager.getOrientation(matrice, orientation)
        val cap = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

        // Le capteur tremble : on n'avance que de 12 % vers la nouvelle valeur, et
        // on ne previent l'ecran que si le cap a bouge de plus de 1,5 degre.
        capLisse = if (capLisse.isNaN()) cap else (capLisse + 0.12f * ecart(capLisse, cap) + 360f) % 360f
        if (capAnnonce.isNaN() || abs(ecart(capAnnonce, capLisse)) > 1.5f) {
            capAnnonce = capLisse
            onCap(capLisse)
        }
    }

    /** Ecart entre deux caps par le plus court chemin : de 359 a 1 degre, c'est +2, pas -358. */
    private fun ecart(de: Float, vers: Float): Float {
        var delta = vers - de
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
