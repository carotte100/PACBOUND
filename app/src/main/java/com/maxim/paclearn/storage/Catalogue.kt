package com.maxim.paclearn.storage

import android.content.Context
import com.maxim.paclearn.model.Amelioration
import com.maxim.paclearn.model.Carte
import org.json.JSONObject

/**
 * Tout ce qui existe dans le jeu : les cartes qu'on peut obtenir et les ameliorations
 * de la boutique. Lu dans assets/data.json en attendant le serveur.
 */
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
