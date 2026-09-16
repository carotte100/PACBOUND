package com.maxim.paclearn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.IOException
import kotlin.math.cos

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var orbOverlay: OrbOverlay? = null
    private var orbsSpawnedAt: GeoPoint? = null
    private var spawnJob: Job? = null
    private var trackingStarted = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            startTracking()
        } else {
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Les serveurs de tuiles exigent un User-Agent identifiant l'app.
        // Le cache est force dans le stockage prive pour ne demander aucune
        // permission de stockage, quelle que soit la version d'Android.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationOverlay = object : MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView) {
            override fun onLocationChanged(location: Location, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)
                onPlayerLocation(GeoPoint(location.latitude, location.longitude))
            }
        }.apply {
            val marker = playerMarkerBitmap()
            setPersonIcon(marker)
            setDirectionIcon(marker)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionAnchor(0.5f, 0.5f)
        }

        binding.mapView.apply {
            setTileSource(CARTO_NO_LABELS)
            setMultiTouchControls(true)
            controller.setZoom(WAITING_ZOOM)
            controller.setCenter(WAITING_POSITION)
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

        // Un premier fix GPS peut prendre des minutes en interieur : on part de la
        // derniere position connue du telephone, le GPS affinera ensuite.
        val lastKnown = lastKnownLocation()
        if (lastKnown != null) {
            locationOverlay.onLocationChanged(lastKnown, null)
        } else {
            Toast.makeText(this, R.string.searching_position, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }

    private fun onPlayerLocation(position: GeoPoint) {
        val spawnedAt = orbsSpawnedAt
        if (spawnedAt == null) {
            binding.mapView.controller.setZoom(PLAY_ZOOM)
            binding.mapView.controller.setCenter(position)
        }
        if (spawnedAt == null || spawnedAt.distanceToAsDouble(position) > RESPAWN_DISTANCE_M) {
            spawnOrbsAround(position)
        }
    }

    private fun spawnOrbsAround(center: GeoPoint) {
        orbsSpawnedAt = center
        spawnJob?.cancel()
        spawnJob = lifecycleScope.launch {
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
            orbOverlay?.let { binding.mapView.overlays.remove(it) }
            // Les orbes passent sous le curseur pour que le joueur reste visible.
            orbOverlay = OrbOverlay(orbs, radiusPx).also { binding.mapView.overlays.add(0, it) }
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

    private fun playerMarkerBitmap(): Bitmap {
        val density = resources.displayMetrics.density
        val size = (PLAYER_MARKER_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)
        paint.color = Color.rgb(33, 150, 243)
        canvas.drawCircle(center, center, center - 3 * density, paint)
        return bitmap
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
        val CARTO_NO_LABELS = XYTileSource(
            "CartoLightNoLabels", 0, 20, 512, "@2x.png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/light_nolabels/",
                "https://b.basemaps.cartocdn.com/light_nolabels/",
                "https://c.basemaps.cartocdn.com/light_nolabels/"
            ),
            "© OpenStreetMap contributors © CARTO"
        )

        // Vue d'attente centree sur la France, tant que la position est inconnue.
        val WAITING_POSITION = GeoPoint(46.6, 2.4)
        const val WAITING_ZOOM = 6.0
        const val PLAY_ZOOM = 17.0

        const val SPAWN_RADIUS_M = 400.0
        const val RESPAWN_DISTANCE_M = 200.0
        const val ORB_SPACING_M = 20.0
        const val ORB_RADIUS_DP = 6f
        const val PLAYER_MARKER_DP = 26f
        const val METERS_PER_DEGREE = 111_320.0
    }
}
