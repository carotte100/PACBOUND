package com.maxim.paclearn.model

import org.json.JSONObject

/**
 * Modèle représentant une amélioration de boutique Riftbound.
 */
data class Amelioration(
    val id: String,
    val rarete: String,
    val text: String,
    val effets: Map<String, String>,
    val prix: Int
) {
    companion object {
        fun fromJson(id: String, json: JSONObject): Amelioration {
            val effetsMap = mutableMapOf<String, String>()
            val effetObj = json.optJSONObject("effet")
            effetObj?.let {
                val keys = it.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    effetsMap[key] = it.optString(key, "")
                }
            }

            return Amelioration(
                id = id,
                rarete = json.optString("rarete", "commune"),
                text = json.optString("text", ""),
                effets = effetsMap,
                prix = json.optInt("prix", 0)
            )
        }
    }
}

