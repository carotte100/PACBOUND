package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.cos

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationProvider: GpsMyLocationProvider
    private lateinit var player: MyLocationNewOverlay
    private var orbs: OrbOverlay? = null
    private var spawnedAt: GeoPoint? = null

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startTracking()
        else showStatus(R.string.location_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showStatus(R.string.searching_position)

        locationProvider = GpsMyLocationProvider(this)
        player = object : MyLocationNewOverlay(locationProvider, binding.mapView) {
            override fun onLocationChanged(location: Location, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)
                onPlayerMoved(GeoPoint(location.latitude, location.longitude))
            }
        }

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(PLAY_ZOOM)
            overlays.add(CopyrightOverlay(context))
            overlays.add(player)
        }

        if (hasLocationPermission()) startTracking()
        else askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun startTracking() {
        // Le suivi continu : chaque nouvelle position redessine le personnage
        // et recentre la carte.
        player.enableMyLocation()
        player.enableFollowLocation()

        useLastKnownLocation()
        retryUntilLocated()
    }

    /**
     * Un premier point GPS peut prendre des minutes en interieur. On reprend la
     * derniere position connue du telephone en passant par le fournisseur, pour
     * que le personnage soit dessine au lieu d'attendre un point tout neuf.
     */
    @SuppressLint("MissingPermission")
    private fun useLastKnownLocation(): Boolean {
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val last = manager.allProviders
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }

        if (last != null) {
            locationProvider.onLocationChanged(last)
            return true
        }
        val anyProviderOn = manager.allProviders.any { manager.isProviderEnabled(it) }
        showStatus(if (anyProviderOn) R.string.searching_position else R.string.location_off)
        return false
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
        binding.status.visibility = View.GONE
        val from = spawnedAt
        if (from == null) binding.mapView.controller.setCenter(position)
        if (from == null || from.distanceToAsDouble(position) > RESPAWN_M) spawn(position)
    }

    private fun spawn(center: GeoPoint) {
        spawnedAt = center
        lifecycleScope.launch {
            val roads = try {
                withContext(Dispatchers.IO) { fetchRoads(bboxAround(center), packageName) }
            } catch (e: Exception) {
                showStatus(R.string.roads_unavailable)
                return@launch
            }
            orbs?.let { binding.mapView.overlays.remove(it) }
            orbs = OrbOverlay(orbsAlong(roads, ORB_SPACING_M), resources.displayMetrics.density)
                .also { binding.mapView.overlays.add(0, it) }
            binding.mapView.invalidate()
        }
    }

    private fun showStatus(messageId: Int) {
        binding.status.setText(messageId)
        binding.status.visibility = View.VISIBLE
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
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    private companion object {
        const val PLAY_ZOOM = 18.0
        const val SPAWN_RADIUS_M = 400.0
        const val RESPAWN_M = 200.0
        const val ORB_SPACING_M = 90.0
        const val RETRY_MS = 10_000L
    }
}
