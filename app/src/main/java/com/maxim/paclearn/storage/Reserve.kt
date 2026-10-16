package com.maxim.paclearn.storage

import android.content.Context
import org.json.JSONArray

/**
 * Les cartes mises de cote dans la boutique pour les acheter a une prochaine partie.
 * On ne garde que leur identifiant : la carte elle-meme vient du catalogue.
 */
class Reserve(context: Context) {

    private val prefs = context.getSharedPreferences("reserve", Context.MODE_PRIVATE)

    fun ids(): List<String> {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        return (0 until json.length()).map { json.getString(it) }
    }

    fun save(ids: List<String>) {
        prefs.edit().putString(KEY, JSONArray(ids).toString()).apply()
    }

    companion object {
        private const val KEY = "cartes"
        const val MAX = 2
    }
}
