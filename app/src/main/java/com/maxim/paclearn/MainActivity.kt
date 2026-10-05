package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import java.io.File
import kotlin.math.cos

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var playerOverlay: PlayerOverlay
    private lateinit var compass: Compass
    private var orbs: OrbOverlay? = null
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

    private var money by mutableStateOf(0)
    private var time by mutableStateOf(0) // temps restant en sec

    private var status by mutableStateOf<Int?>(R.string.searching_position)

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startTracking()
        else status = R.string.location_denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(PLAY_ZOOM)
            overlays.add(CopyrightOverlay(context))
        }

        // Duree choisie sur l'ecran d'accueil, convertie en secondes.
        time = intent.getIntExtra(TimeSelectActivity.EXTRA_MINUTES, DEFAULT_MINUTES) * 60

        playerOverlay = PlayerOverlay(resources.displayMetrics.density)
        mapView.overlays.add(playerOverlay)

        compass = Compass(this) { heading ->
            playerOverlay.heading = heading
            mapView.postInvalidate()
        }

        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                    Text(
                        text = timeConverter(),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .width(120.dp)
                            .padding(12.dp)
                            .background(Color(0xCC000000))
                            .padding(10.dp)
                    )

                    Text(
                        text = "$money €",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0xCC000000))
                            .padding(10.dp)
                    )

                    status?.let { message ->
                        Text(
                            text = stringResource(message),
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(Color(0xCC000000))
                                .padding(10.dp)
                        )
                    }
                }
            }
        }

        if (hasLocationPermission()) startTracking()
        else askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )

        startCountdown()
    }

    private fun startCountdown() {
        lifecycleScope.launch {
            while (time > 0) {
                delay(1000)
                time--
            }
            onTimeUp()
        }
    }

    private fun onTimeUp() {
        // Fin de partie : pour l'instant on revient a l'ecran de choix.
        finish()
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
        retryUntilLocated()
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

        val candidateError = if (candidate.hasAccuracy()) candidate.accuracy else WORST_ACCURACY_M
        val previousError = if (previous.hasAccuracy()) previous.accuracy else WORST_ACCURACY_M

        if (candidateError < previousError / 2f) return true
        if (candidateError > previousError * 2f) return false

        val moved = candidate.distanceTo(previous)
        if (moved < MIN_MOVE_M) return false

        val seconds = (candidate.time - previous.time) / 1000.0
        if (seconds > 0 && moved / seconds > MAX_SPEED_MS) return false
        return true
    }

    private fun acceptFix(location: Location) {
        if (!isPlausible(location)) return
        lastFix = location
        onPlayerMoved(GeoPoint(location.latitude, location.longitude))
    }

    private fun timeConverter(): String {
        val h = time / 3600
        val m = (time % 3600) / 60
        val s = time % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    /**
     * Un premier point GPS peut prendre des minutes en interieur : on demarre
     * sur la derniere position connue du telephone, si elle est credible.
     */
    @SuppressLint("MissingPermission")
    private fun useLastKnownLocation() {
        val last = locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }

        if (last != null) {
            acceptFix(last)
            return
        }
        val anyProviderOn = locationManager.allProviders.any { locationManager.isProviderEnabled(it) }
        status = if (anyProviderOn) R.string.searching_position else R.string.location_off
    }

    private fun retryUntilLocated() {
        lifecycleScope.launch {
            while (spawnedAt == null) {
                delay(RETRY_MS)
                useLastKnownLocation()
            }
        }
    }

    private fun onPlayerMoved(position: GeoPoint) {
        status = null
        playerOverlay.position = position
        mapView.controller.animateTo(position)
        mapView.invalidate()

        val from = spawnedAt
        if (from == null || from.distanceToAsDouble(position) > RESPAWN_M) spawn(position)
    }

    private fun spawn(center: GeoPoint) {
        spawnedAt = center
        spawnJob?.cancel()
        // Les serveurs Overpass publics tombent regulierement : on reessaie
        // au lieu de laisser la partie sans orbes.
        spawnJob = lifecycleScope.launch {
            while (true) {
                try {
                    val roads = withContext(Dispatchers.IO) {
                        fetchRoads(bboxAround(center), packageName)
                    }
                    orbs?.let { mapView.overlays.remove(it) }
                    orbs = OrbOverlay(orbsAlong(roads, ORB_SPACING_M), resources.displayMetrics.density)
                        .also { mapView.overlays.add(0, it) }
                    mapView.invalidate()
                    status = null
                    return@launch
                } catch (e: Exception) {
                    status = R.string.roads_unavailable
                    delay(RETRY_MS)
                }
            }
        }
    }

    private fun bboxAround(center: GeoPoint): BoundingBox {
        val dLat = SPAWN_RADIUS_M / 111_320.0
        val dLon = dLat / cos(Math.toRadians(center.latitude))
        return BoundingBox(
            center.latitude + dLat, center.longitude + dLon,
            center.latitude - dLat, center.longitude - dLon
        )
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
        const val PLAY_ZOOM = 18.0
        const val SPAWN_RADIUS_M = 400.0
        const val RESPAWN_M = 200.0
        const val ORB_SPACING_M = 90.0
        const val RETRY_MS = 10_000L
        const val DEFAULT_MINUTES = 5

        val TRACKED_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val UPDATE_MS = 1_000L
        const val WORST_ACCURACY_M = 100f // hypothese quand le point n'annonce rien
        const val MIN_MOVE_M = 3f         // en-deca, c'est du tremblement sur place
        const val MAX_SPEED_MS = 8.0      // un pieton ne sprinte pas plus vite
    }
}
