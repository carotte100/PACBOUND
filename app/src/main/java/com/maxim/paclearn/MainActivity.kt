package com.maxim.paclearn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.maxim.paclearn.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(START_ZOOM)
            controller.setCenter(START_POSITION)
        }
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
        val START_POSITION = GeoPoint(48.8584, 2.2945)
        const val START_ZOOM = 17.0
    }
}
