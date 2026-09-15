package com.maxim.paclearn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment
import com.google.android.gms.maps.model.LatLng
import com.maxim.paclearn.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val panoramaFragment = supportFragmentManager
            .findFragmentById(R.id.streetViewPanorama) as SupportStreetViewPanoramaFragment

        panoramaFragment.getStreetViewPanoramaAsync { panorama ->
            panorama.isStreetNamesEnabled = false
            panorama.isUserNavigationEnabled = true
            if (savedInstanceState == null) {
                panorama.setPosition(START_POSITION, PANORAMA_SEARCH_RADIUS_M)
            }
        }
    }

    private companion object {
        val START_POSITION = LatLng(48.8584, 2.2945)
        const val PANORAMA_SEARCH_RADIUS_M = 200
    }
}
