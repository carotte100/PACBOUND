package com.maxim.paclearn.model

import org.json.JSONObject

/** Une amelioration de la boutique : ne dure que la partie en cours. */
data class Amelioration(
    val id: String,
    val rarete: String,
    val text: String,
    val effets: Map<String, String>,
    val prix: Int
) {
    companion object {
        fun fromJson(id: String, json: JSONObject): Amelioration {
            val effets = mutableMapOf<String, String>()
            json.optJSONObject("effet")?.let { effet ->
                effet.keys().forEach { effets[it] = effet.optString(it, "") }
            }
            return Amelioration(
                id = id,
                rarete = json.optString("rarete", "commune"),
                text = json.optString("text", ""),
                effets = effets,
                prix = json.optInt("prix", 0)
            )
        }
    }
}
