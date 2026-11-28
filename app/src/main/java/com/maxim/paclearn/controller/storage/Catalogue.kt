package com.maxim.paclearn.controller.storage

import android.content.Context
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte
import org.json.JSONObject

/** Toutes les cartes et ameliorations du jeu, lues dans assets/data.json. */
class Catalogue(val cartes: List<Carte>, val ameliorations: List<Amelioration>) {

    companion object {
        fun load(context: Context): Catalogue {
            val root = JSONObject(readJsonFromAssets(context, "data.json"))

            val cartesJson = root.optJSONObject("cartes") ?: JSONObject()
            val cartes = cartesJson.keys().asSequence()
                .map { Carte.fromJson(cartesJson.getJSONObject(it)) }
                .toList()

            val ameliorationsJson = root.optJSONObject("ameliorations") ?: JSONObject()
            val ameliorations = ameliorationsJson.keys().asSequence()
                .map { Amelioration.fromJson(it, ameliorationsJson.getJSONObject(it)) }
                .toList()

            return Catalogue(cartes, ameliorations)
        }
    }
}
