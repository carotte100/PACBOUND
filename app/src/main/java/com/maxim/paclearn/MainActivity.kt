package com.maxim.paclearn

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.maxim.paclearn.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sert les assets sous une vraie origine https, sinon les appels a
        // l'API Mapillary sont bloques par CORS depuis une page file://.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        binding.panoramaView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    val token = JSONObject.quote(BuildConfig.MAPILLARY_TOKEN)
                    view.evaluateJavascript("startViewer($token, $START_LAT, $START_LON);", null)
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/mapillary.html")
        }
    }

    private companion object {
        const val START_LAT = 48.8584
        const val START_LON = 2.2945
    }
}
