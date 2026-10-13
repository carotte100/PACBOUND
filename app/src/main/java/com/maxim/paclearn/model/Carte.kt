package com.maxim.paclearn.model

import android.content.Context
import org.json.JSONObject

/**
 * Modèle représentant une carte Riftbound.
 * Compatible à la fois avec le format du serveur distant (boutique / catalogue)
 * et avec le format de la collection locale stockée sur le téléphone.
 */
data class Carte(
    val nom: String,
    val set: String,
    val rarete: String,
    val variant: String = "normale",
    val couleur: String,
    val type: String, // "unite", "sort", "equipement"
    val cost: Int = 0,
    val power: Int = 0,
    val might: Int = 0,
    val tag: String = "",
    val motsCles: String = "",
    val image: String = "", // ex: "SFD-005", "RAD-043" (Set-Numéro)
    val foil: Boolean = false,
    var quantity: Int = 1
) {
    /**
     * Identifiant unique pour distinguer une carte dans la collection
     * selon son nom, son set, sa variante et son état foil.
     */
    fun uniqueId(): String =
        "${nom.lowercase().replace(" ", "_")}_${set.lowercase()}_${variant.lowercase()}_${if (foil) "foil" else "normal"}"

    /**
     * Nom du fichier drawable Android (minuscules, tirets remplacés par des underscores),
     * ex: "SFD-005" -> "sfd_005" pour res/drawable/sfd_005.webp
     */
    fun drawableName(): String =
        if (image.isNotEmpty()) image.lowercase().replace("-", "_")
        else nom.lowercase()
            .replace(" ", "_")
            .replace(",", "")
            .replace("'", "_")
            .replace("ê", "e").replace("é", "e").replace("è", "e").replace("ô", "o")

    /**
     * Récupère l'identifiant de ressource drawable (R.drawable.xxx) si présent dans res/drawable/.
     */
    fun getDrawableResId(context: Context): Int {
        val resName = drawableName()
        return context.resources.getIdentifier(resName, "drawable", context.packageName)
    }

    /**
     * Convertit la carte vers le format JSON de collection locale ("collec").
     */
    fun toCollectionJson(): JSONObject {
        val json = JSONObject()
        json.put(KEY_TYPE_OBJET, "carte")
        json.put(KEY_NOM, nom)
        json.put(KEY_SET, set)
        json.put(KEY_RARETE, rarete)
        json.put(KEY_VARIANT, variant)
        json.put(KEY_FOIL, if (foil) "oui" else "non")
        json.put(KEY_QUANTITY, quantity)
        json.put(KEY_COULEUR, couleur)
        json.put(KEY_TYPE, type)

        if (image.isNotEmpty()) json.put(KEY_IMAGE, image)
        if (cost > 0) json.put(KEY_COST, cost)
        if (power > 0) json.put(KEY_POWER, power)
        if (might > 0) json.put(KEY_MIGHT, might)
        if (tag.isNotEmpty()) json.put(KEY_TAG, tag)
        if (motsCles.isNotEmpty()) json.put(KEY_MOTS_CLES, motsCles)

        return json
    }

    /**
     * Convertit la carte vers le format JSON du catalogue serveur.
     */
    fun toServerJson(): JSONObject {
        val json = JSONObject()
        json.put(KEY_TYPE_OBJET, "carte")
        json.put(KEY_NOM, nom)
        json.put(KEY_SET, set)
        json.put(KEY_RARETE, rarete)
        json.put(KEY_VARIANT, variant)
        json.put(KEY_COULEUR, couleur)
        json.put(KEY_TYPE, type)
        json.put(KEY_COST, cost)
        json.put(KEY_POWER, power)
        json.put(KEY_MIGHT, might)
        json.put(KEY_TAG, tag)
        json.put(KEY_MOTS_CLES, motsCles)
        if (image.isNotEmpty()) json.put(KEY_IMAGE, image)
        return json
    }

    companion object {
        const val KEY_TYPE_OBJET = "type_objet"
        const val KEY_TYPE = "type"
        const val KEY_NOM = "nom"
        const val KEY_SET = "set"
        const val KEY_RARETE = "rarete"
        const val KEY_VARIANT = "variant"
        const val KEY_FOIL = "foil"
        const val KEY_QUANTITY = "quantity"
        const val KEY_COULEUR = "couleur"
        const val KEY_COST = "cost"
        const val KEY_POWER = "power"
        const val KEY_MIGHT = "might"
        const val KEY_TAG = "tag"
        const val KEY_MOTS_CLES = "mots_cles"
        const val KEY_IMAGE = "image"

        /**
         * Désérialise un JSONObject en objet Carte, qu'il vienne du serveur distant
         * ou de la collection locale.
         */
        fun fromJson(json: JSONObject): Carte {
            val foilValue = when {
                json.has(KEY_FOIL) -> {
                    val rawFoil = json.opt(KEY_FOIL)
                    when (rawFoil) {
                        is Boolean -> rawFoil
                        is String -> rawFoil.equals("oui", ignoreCase = true) || rawFoil.equals("true", ignoreCase = true)
                        else -> false
                    }
                }
                else -> false
            }

            return Carte(
                nom = json.optString(KEY_NOM, ""),
                set = json.optString(KEY_SET, ""),
                rarete = json.optString(KEY_RARETE, ""),
                variant = json.optString(KEY_VARIANT, "normale"),
                couleur = json.optString(KEY_COULEUR, ""),
                type = json.optString(KEY_TYPE, "unite"),
                cost = json.optInt(KEY_COST, 0),
                power = json.optInt(KEY_POWER, 0),
                might = json.optInt(KEY_MIGHT, 0),
                tag = json.optString(KEY_TAG, ""),
                motsCles = json.optString(KEY_MOTS_CLES, ""),
                image = json.optString(KEY_IMAGE, ""),
                foil = foilValue,
                quantity = json.optInt(KEY_QUANTITY, 1)
            )
        }
    }
}
