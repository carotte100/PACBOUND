package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.cos

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationProvider: GpsMyLocationProvider
    private lateinit var player: MyLocationNewOverlay
    private var orbs: OrbOverlay? = null
    private var spawnedAt: GeoPoint? = null

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

        locationProvider = GpsMyLocationProvider(this)
        player = object : MyLocationNewOverlay(locationProvider, mapView) {
            override fun onLocationChanged(location: Location, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)
                onPlayerMoved(GeoPoint(location.latitude, location.longitude))
            }
        }
        mapView.overlays.add(player)

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

    private fun startTracking() {
        player.enableMyLocation()
        player.enableFollowLocation()
        useLastKnownLocation()
        retryUntilLocated()
    }

    private fun timeConverter(): String {
        val h = time / 3600
        val m = (time % 3600) / 60
        val s = time % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    /**
     * Un premier point GPS peut prendre des minutes en interieur. On reprend la
     * derniere position connue du telephone en passant par le fournisseur, pour
     * que le personnage soit dessine au lieu d'attendre un point tout neuf.
     */
    @SuppressLint("MissingPermission")
    private fun useLastKnownLocation() {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val last = manager.allProviders
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }

        if (last != null) {
            locationProvider.onLocationChanged(last)
            return
        }
        val anyProviderOn = manager.allProviders.any { manager.isProviderEnabled(it) }
        status = if (anyProviderOn) R.string.searching_position else R.string.location_off
    }

    private fun retryUntilLocated() {
        lifecycleScope.launch {
            while (spawnedAt == null) {
                delay(RETRY_MS)
                // osmdroid ne s'abonne qu'aux fournisseurs actifs au moment de
                // l'abonnement : on le relance pour capter ceux actives depuis.
                player.enableMyLocation()
                useLastKnownLocation()
            }
        }
    }

    private fun onPlayerMoved(position: GeoPoint) {
        status = null
        val from = spawnedAt
        if (from == null) mapView.controller.setCenter(position)
        if (from == null || from.distanceToAsDouble(position) > RESPAWN_M) spawn(position)
    }

    private fun spawn(center: GeoPoint) {
        spawnedAt = center
        lifecycleScope.launch {
            val roads = try {
                withContext(Dispatchers.IO) { fetchRoads(bboxAround(center), packageName) }
            } catch (e: Exception) {
                status = R.string.roads_unavailable
                return@launch
            }
            orbs?.let { mapView.overlays.remove(it) }
            orbs = OrbOverlay(orbsAlong(roads, ORB_SPACING_M), resources.displayMetrics.density)
                .also { mapView.overlays.add(0, it) }
            mapView.invalidate()
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
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    private companion object {
        const val PLAY_ZOOM = 18.0
        const val SPAWN_RADIUS_M = 400.0
        const val RESPAWN_M = 200.0
        const val ORB_SPACING_M = 90.0
        const val RETRY_MS = 10_000L
        const val DEFAULT_MINUTES = 5
    }
}
