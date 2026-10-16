package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.storage.CarteStorage
import com.maxim.paclearn.model.Carte
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

/** L'ecran de jeu : la carte, le joueur qui s'y deplace, les orbes a ramasser et le chrono. */
class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var playerOverlay: PlayerOverlay
    private lateinit var compass: Compass
    private var streetMap: StreetMapOverlay? = null
    private var orbs: OrbOverlay? = null
    private val collected = HashSet<Long>() // cellules (cellOf) des orbes deja ramasses
    private var spawnedAt: GeoPoint? = null
    private var spawnJob: Job? = null

    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var lastFix: Location? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = acceptFix(location)
        @Deprecated("Exige par LocationListener avant l'API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    // Etats Compose : le HUD se redessine seul quand ils changent.
    private var money by mutableStateOf(0)
    private var time by mutableStateOf(0) // temps restant en sec
    private var status by mutableStateOf<Int?>(R.string.searching_position)
    private var shopOpen by mutableStateOf(false)
    private var confirmQuit by mutableStateOf(false)
    private var stats by mutableStateOf<RunStats?>(null) // non nul : partie finie, recap affiche

    private val collection by lazy { CarteStorage(this) }
    private val shop by lazy { RunShop(Catalogue.load(this), Reserve(this)) }

    // Pour le recap de fin de partie.
    private var totalSeconds = 0
    private var orbsCollected = 0
    private var earned = 0
    private var spent = 0
    private val bought = ArrayList<Carte>()
    private var timerJob: Job? = null

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startTracking()
        else status = R.string.location_denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La carte passe sous les barres systeme, aux icones sombres sur le vert.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        mapView = MapView(this).apply {
            // Pas de tuiles : StreetMapOverlay dessine les rues sur ce fond vert.
            overlayManager.tilesOverlay.isEnabled = false
            setUseDataConnection(false)
            // Surtout pas setBackgroundColor : osmdroid le detourne vers la couleur
            // d'attente des tuiles, qui ne sont plus dessinees, et la carte restait
            // transparente sur le fond nuit de la fenetre.
            background = ColorDrawable(StreetMapOverlay.GROUND)
            setMultiTouchControls(true)
            // Le pincement suffit : les boutons +/- encombraient l'ecran.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // Pas de dezoom : la partie se joue autour du joueur, et plus loin on
            // verrait le bord de la zone chargee. Zoomer pour voir de pres reste possible.
            setMinZoomLevel(PLAY_ZOOM)
            setMaxZoomLevel(MAX_ZOOM)
            controller.setZoom(PLAY_ZOOM)
        }

        // Duree choisie sur l'ecran d'accueil, convertie en secondes.
        totalSeconds = intent.getIntExtra(TimeSelectActivity.EXTRA_MINUTES, DEFAULT_MINUTES) * 60
        time = totalSeconds

        playerOverlay = PlayerOverlay(resources.displayMetrics.density, COLLECT_RADIUS_M)
        mapView.overlays.add(playerOverlay)

        compass = Compass(this) { heading ->
            playerOverlay.heading = heading
            mapView.postInvalidate()
        }

        setContent {
            PacTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    Hud(
                        secondsLeft = time,
                        money = money,
                        status = status,
                        onShop = { shopOpen = true },
                        onQuit = { confirmQuit = true }
                    )
                    // Le retour ne quitte pas d'un coup : on rappelle ce qui sera perdu.
                    BackHandler(enabled = stats == null) { confirmQuit = true }
                    if (shopOpen) {
                        ShopScreen(
                            shop = shop,
                            money = money,
                            possedees = { collection.find(it.uniqueId())?.quantity ?: 0 },
                            onAcheterCarte = ::buyCard,
                            onAcheterReservee = ::buyReserved,
                            onAcheterAmelioration = ::buyUpgrade,
                            onRelancer = ::rerollShop,
                            onFermer = { shopOpen = false }
                        )
                    }
                    if (confirmQuit) {
                        QuitDialog(
                            money = money,
                            ameliorations = shop.actives.size,
                            onRester = { confirmQuit = false },
                            onQuitter = { endRun(timeUp = false) }
                        )
                    }
                    stats?.let { RecapScreen(it, onTerminer = ::finish) }
                }
            }
        }

        if (hasLocationPermission()) startTracking()
        else askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )

        timerJob = lifecycleScope.launch {
            while (time > 0) {
                delay(1000)
                time--
            }
            endRun(timeUp = true)
        }
    }

    /** Arrete la partie (chrono, GPS, chargements) et affiche le recap. */
    private fun endRun(timeUp: Boolean) {
        if (stats != null) return
        timerJob?.cancel()
        spawnJob?.cancel()
        locationManager.removeUpdates(locationListener)
        shopOpen = false
        confirmQuit = false
        stats = RunStats(
            secondesJouees = totalSeconds - time,
            orbes = orbsCollected,
            gagne = earned,
            depense = spent,
            restant = money,
            cartes = bought.toList(),
            ameliorations = shop.actives.toList(),
            tempsEcoule = timeUp
        )
    }

    // La boutique peut etre ouverte pendant qu'on marche : on reverifie l'argent a chaque achat.
    private fun pay(amount: Int): Boolean {
        if (stats != null || money < amount) return false
        money -= amount
        spent += amount
        return true
    }

    private fun buyCard(index: Int) {
        val offre = shop.cartes[index]
        if (offre.etat != EtatOffre.EN_VENTE || !pay(prixCarte(offre.carte))) return
        collection.addOrIncrement(offre.carte)
        bought.add(offre.carte)
        shop.vendreCarte(index)
    }

    private fun buyReserved(carte: Carte) {
        if (!pay(prixCarte(carte))) return
        collection.addOrIncrement(carte)
        bought.add(carte)
        shop.retirerDeLaReserve(carte)
    }

    private fun buyUpgrade(index: Int) {
        val amelioration = shop.ameliorations[index] ?: return
        if (!pay(amelioration.prix)) return
        shop.vendreAmelioration(index)
    }

    private fun rerollShop() {
        if (!pay(PRIX_RELANCE)) return
        shop.relancer()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        for (provider in TRACKED_PROVIDERS) {
            if (provider in locationManager.allProviders) {
                locationManager.requestLocationUpdates(provider, UPDATE_MS, 0f, locationListener)
            }
        }
        useLastKnownLocation()
        // Sans premier point, ni joueur ni orbes : on relit regulierement la derniere position.
        lifecycleScope.launch {
            while (spawnedAt == null) {
                delay(RETRY_MS)
                useLastKnownLocation()
            }
        }
    }

    /**
     * Le Wi-Fi renvoie des positions precises a quelques centaines de metres :
     * melangees aux points GPS, elles teleportent le joueur. On compare donc
     * chaque point a celui en cours plutot que de fixer un seuil absolu, qui
     * rejetterait tout en interieur. Les micro-ecarts sont ignores pour que le
     * personnage ne tremble pas a l'arret.
     */
    private fun isPlausible(candidate: Location): Boolean {
        val previous = lastFix ?: return true

        val candidateError = errorOf(candidate)
        val previousError = errorOf(previous)
        if (candidateError < previousError / 2f) return true

        // Horloge monotone : l'heure d'un point GPS vient des satellites, celle
        // d'un point Wi-Fi vient du telephone, que rien ne resynchronise hors ligne.
        val seconds = (candidate.elapsedRealtimeNanos - previous.elapsedRealtimeNanos)
            .coerceAtLeast(0) / 1e9

        // Un point perd sa precision en vieillissant, le joueur ayant pu marcher
        // depuis. Sans cela, un vieux point tres precis (derniere position connue,
        // dernier point avant les arbres) bloquait tous les points GPS suivants,
        // et hors ligne le GPS est la seule source : le joueur restait fige.
        val previousErrorNow = previousError + seconds * WALK_SPEED_MS
        if (candidateError > previousErrorNow * 2) return false

        val moved = candidate.distanceTo(previous)
        if (moved < MIN_MOVE_M) return false

        if (seconds > 0 && moved / seconds > MAX_SPEED_MS) return false
        return true
    }

    private fun errorOf(location: Location) =
        if (location.hasAccuracy()) location.accuracy else WORST_ACCURACY_M

    private fun acceptFix(location: Location) {
        if (!isPlausible(location)) return
        lastFix = location
        onPlayerMoved(GeoPoint(location.latitude, location.longitude))
    }

    /**
     * Un premier point GPS peut prendre des minutes en interieur : on demarre
     * sur la derniere position connue du telephone, si elle est credible.
     */
    @SuppressLint("MissingPermission")
    private fun useLastKnownLocation() {
        val last = locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }

        if (last != null) {
            acceptFix(last)
            return
        }
        val anyProviderOn = locationManager.allProviders.any { locationManager.isProviderEnabled(it) }
        status = if (anyProviderOn) R.string.searching_position else R.string.location_off
    }

    private fun onPlayerMoved(position: GeoPoint) {
        if (stats != null) return // partie finie : plus rien a ramasser
        // Seuls les messages de recherche tombent : celui du chargement de la
        // carte reste tant qu'elle n'est pas arrivee.
        if (status == R.string.searching_position || status == R.string.location_off) status = null
        playerOverlay.position = position
        mapView.controller.animateTo(position)
        mapView.invalidate()

        collectOrbs(position)

        val from = spawnedAt
        if (from == null || from.distanceToAsDouble(position) > RESPAWN_M) spawn(position)
    }

    /** Ramasse les orbes autour du joueur et credite leur valeur. */
    private fun collectOrbs(position: GeoPoint) {
        val overlay = orbs ?: return // carte encore en chargement : rien a ramasser
        val picked = overlay.collectNear(position, COLLECT_RADIUS_M)
        if (picked.isEmpty()) return

        money += picked.size * ORB_VALUE
        earned += picked.size * ORB_VALUE
        orbsCollected += picked.size
        // Retenus pour qu'ils ne reapparaissent pas au prochain rechargement des rues.
        picked.forEach { collected.add(cellOf(it)) }
        mapView.invalidate()
    }

    /** Charge la zone autour de [center] : la carte a dessiner et ses orbes. */
    private fun spawn(center: GeoPoint) {
        spawnedAt = center
        spawnJob?.cancel()
        if (orbs == null) status = R.string.loading_map
        // Les serveurs Overpass publics tombent regulierement : on reessaie
        // au lieu de laisser la partie sans carte.
        spawnJob = lifecycleScope.launch {
            while (true) {
                try {
                    val area = withContext(Dispatchers.IO) {
                        fetchArea(center, SPAWN_RADIUS_M, packageName)
                    }
                    val density = resources.displayMetrics.density
                    // Des milliers de tests contre les batiments : hors du fil de l'interface.
                    val (placed, map) = withContext(Dispatchers.Default) {
                        placeOrbs(area, center, SPAWN_RADIUS_M, ORB_SPACING_M) to
                            StreetMapOverlay(area, center, density)
                    }
                    // L'ancienne zone reste affichee jusqu'ici : pas de carte vide pendant le chargement.
                    streetMap?.let { mapView.overlays.remove(it) }
                    orbs?.let { mapView.overlays.remove(it) }
                    streetMap = map.also { mapView.overlays.add(0, it) }
                    orbs = OrbOverlay(placed.filterNot { cellOf(it) in collected }, density)
                        .also { mapView.overlays.add(1, it) }
                    mapView.invalidate()
                    status = null
                    return@launch
                } catch (e: CancellationException) {
                    // Remplace par un chargement plus proche du joueur : ce n'est
                    // pas un echec, et afficher l'erreur ecraserait son message.
                    throw e
                } catch (e: Exception) {
                    status = R.string.roads_unavailable
                    delay(RETRY_MS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        compass.start()
    }

    override fun onPause() {
        compass.stop()
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        // Le suivi continue ecran eteint pendant la partie, mais pas apres.
        locationManager.removeUpdates(locationListener)
        super.onDestroy()
    }

    private companion object {
        // Au zoom 18, la zone de ramassage de 15 m est plus petite que le
        // smiley et disparait dessous ; au 19, elle deborde autour de lui.
        const val PLAY_ZOOM = 19.0
        const val MAX_ZOOM = 21.0
        const val SPAWN_RADIUS_M = 400.0
        const val RESPAWN_M = 200.0
        const val ORB_SPACING_M = 90.0
        const val COLLECT_RADIUS_M = 15.0 // le GPS d'un telephone se trompe de 5 a 15 m
        const val ORB_VALUE = 2           // euros par orbe : voir les prix en haut de Shop.kt
        const val RETRY_MS = 10_000L
        const val DEFAULT_MINUTES = 5

        val TRACKED_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val UPDATE_MS = 1_000L
        const val WORST_ACCURACY_M = 100f // hypothese quand le point n'annonce rien
        const val MIN_MOVE_M = 3f         // en-deca, c'est du tremblement sur place
        const val MAX_SPEED_MS = 8.0      // un pieton ne sprinte pas plus vite
        const val WALK_SPEED_MS = 1.5     // derive supposee d'un point qui vieillit
    }
}

/**
 * Cap du telephone en degres, 0 = nord. S'appuie sur le vecteur de rotation
 * plutot que sur le capteur d'orientation, obsolete et absent de certains
 * telephones. Suppose l'ecran en portrait.
 */
private class Compass(context: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var smoothed = Float.NaN
    private var reported = Float.NaN

    fun start() {
        rotationVector?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = sensors.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val raw = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f

        // Le capteur est nerveux : on avance par petits pas vers la valeur
        // visee, et on ne previent l'ecran que si le cap a vraiment bouge.
        smoothed = if (smoothed.isNaN()) raw else smoothed + SMOOTHING * shortestTurn(smoothed, raw)
        smoothed = (smoothed + 360f) % 360f

        if (reported.isNaN() || abs(shortestTurn(reported, smoothed)) > REPORT_STEP) {
            reported = smoothed
            onHeading(smoothed)
        }
    }

    /** Ecart entre deux caps, en passant par le plus court chemin (-180..180). */
    private fun shortestTurn(from: Float, to: Float): Float {
        var delta = to - from
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val SMOOTHING = 0.12f
        const val REPORT_STEP = 1.5f
    }
}
