package com.maxim.paclearn.storage

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class RemoteJsonFetcher(private val context: Context) {

    private val queue: RequestQueue by lazy {
        Volley.newRequestQueue(context.applicationContext)
    }

    fun fetchJson(url: String, onResult: (JSONObject?) -> Unit) {
        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                Log.d(TAG, "JSON reçu avec succès depuis $url")
                onResult(response)
            },
            { error ->
                Log.e(TAG, "Erreur lors de la récupération du JSON depuis $url : ${error.message}", error)
                error.printStackTrace()
                onResult(null)
            }
        )
        queue.add(request)
    }

    fun postJson(url: String, payload: JSONObject, onResult: (JSONObject?) -> Unit) {
        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            payload,
            { response ->
                Log.d(TAG, "JSON envoyé avec succès vers $url")
                onResult(response)
            },
            { error ->
                Log.e(TAG, "Erreur lors de l'envoi du JSON vers $url : ${error.message}", error)
                error.printStackTrace()
                onResult(null)
            }
        )
        queue.add(request)
    }

    companion object {
        private const val TAG = "RemoteJsonFetcher"

        fun fetch(context: Context, url: String, onResult: (JSONObject?) -> Unit) {
            val queue: RequestQueue = Volley.newRequestQueue(context)
            val request = JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                { response -> onResult(response) },
                { error ->
                    error.printStackTrace()
                    onResult(null)
                }
            )
            queue.add(request)
        }
    }
}

