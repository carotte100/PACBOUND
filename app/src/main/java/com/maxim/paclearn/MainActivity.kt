package com.maxim.paclearn

import android.Manifest
import android.content.pm.PackageManager
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
import org.json.JSONException
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.IOException
import kotlin.math.cos

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var trackingStarted = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            startTracking()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
            spawnOrbsAround(FALLBACK_POSITION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Les serveurs de tuiles OSM exigent un User-Agent identifiant l'app.
        // Le cache est force dans le stockage prive pour ne demander aucune
        // permission de stockage, quelle que soit la version d'Android.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(START_ZOOM)
            controller.setCenter(FALLBACK_POSITION)
            overlays.add(CopyrightOverlay(context))
            overlays.add(locationOverlay)
        }

        if (hasLocationPermission()) {
            startTracking()
        } else {
            locationPermissionRequest.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun startTracking() {
        trackingStarted = true
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        locationOverlay.runOnFirstFix {
            val here = locationOverlay.myLocation ?: return@runOnFirstFix
            runOnUiThread { spawnOrbsAround(here) }
        }
    }

    private fun spawnOrbsAround(center: GeoPoint) {
        lifecycleScope.launch {
            val orbs = try {
                withContext(Dispatchers.IO) {
                    val roads = RoadFetcher.fetchRoads(bboxAround(center, SPAWN_RADIUS_M), packageName)
                    OrbGenerator.alongRoads(roads, ORB_SPACING_M)
                }
            } catch (e: IOException) {
                Toast.makeText(this@MainActivity, R.string.error_roads_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            } catch (e: JSONException) {
                Toast.makeText(this@MainActivity, R.string.error_roads_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }

            val radiusPx = ORB_RADIUS_DP * resources.displayMetrics.density
            // Les orbes passent sous le point de position pour que le joueur reste visible.
            binding.mapView.overlays.add(0, OrbOverlay(orbs, radiusPx))
            binding.mapView.invalidate()
        }
    }

    private fun bboxAround(center: GeoPoint, radiusMeters: Double): BoundingBox {
        val dLat = radiusMeters / METERS_PER_DEGREE
        val dLon = radiusMeters / (METERS_PER_DEGREE * cos(Math.toRadians(center.latitude)))
        return BoundingBox(
            center.latitude + dLat, center.longitude + dLon,
            center.latitude - dLat, center.longitude - dLon
        )
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (trackingStarted) locationOverlay.enableMyLocation()
    }

    override fun onPause() {
        locationOverlay.disableMyLocation()
        binding.mapView.onPause()
        super.onPause()
    }

    private companion object {
        val FALLBACK_POSITION = GeoPoint(48.8584, 2.2945)
        const val START_ZOOM = 17.0
        const val SPAWN_RADIUS_M = 400.0
        const val ORB_SPACING_M = 20.0
        const val ORB_RADIUS_DP = 5f
        const val METERS_PER_DEGREE = 111_320.0
    }
}
