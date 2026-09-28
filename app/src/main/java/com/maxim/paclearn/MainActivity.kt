package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maxim.paclearn.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
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
    private lateinit var player: MyLocationNewOverlay
    private var orbs: OrbOverlay? = null
    private var spawnedAt: GeoPoint? = null

    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) startTracking()
        else Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Les serveurs de tuiles OSM exigent un User-Agent identifiant l'app, et
        // le cache va dans le stockage prive pour ne demander aucune permission.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = object : MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView) {
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

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        player.enableMyLocation()
        player.enableFollowLocation()

        // Un premier fix GPS peut prendre des minutes en interieur : on demarre
        // sur la derniere position connue du telephone.
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val last = manager.allProviders
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
        if (last != null) player.onLocationChanged(last, null)
        else Toast.makeText(this, R.string.searching_position, Toast.LENGTH_LONG).show()
    }

    private fun onPlayerMoved(position: GeoPoint) {
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
                Toast.makeText(this@MainActivity, R.string.roads_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }
            orbs?.let { binding.mapView.overlays.remove(it) }
            orbs = OrbOverlay(orbsAlong(roads, ORB_SPACING_M), resources.displayMetrics.density)
                .also { binding.mapView.overlays.add(0, it) }
            binding.mapView.invalidate()
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
        const val ORB_SPACING_M = 20.0
    }
}
