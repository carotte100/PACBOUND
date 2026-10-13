package com.maxim.paclearn.storage

import android.content.Context
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.storage.utility.file.JSONFileStorage
import org.json.JSONObject

/**
 * Gère le stockage persistant local de la collection de cartes du joueur sur le téléphone.
 * Étend JSONFileStorage<Carte> selon la même architecture que dans 2XKO.
 */
class CarteStorage(context: Context) : JSONFileStorage<Carte>(context, "collection") {

    override fun create(nom: String, obj: Carte): Carte {
        return obj.copy()
    }

    override fun objectToJson(nom: String, obj: Carte): JSONObject {
        return obj.toCollectionJson()
    }

    override fun jsonToObject(json: JSONObject): Carte {
        return Carte.fromJson(json)
    }

    /**
     * Ajoute une carte à la collection locale ou incrémente sa quantité si elle est déjà possédée.
     */
    fun addOrIncrement(carte: Carte, additionalQuantity: Int = 1) {
        val key = carte.uniqueId()
        val existing = find(key)
        if (existing != null) {
            existing.quantity += additionalQuantity
            update(key, existing)
        } else {
            val newCarte = carte.copy(quantity = additionalQuantity)
            insert(key, newCarte)
        }
    }

    /**
     * Retourne la liste complète des cartes possédées dans la collection.
     */
    fun getCollection(): List<Carte> {
        return findAll()
    }

    /**
     * Retire une certaine quantité d'une carte ou la supprime si la quantité tombe à 0.
     */
    fun removeOrDecrement(carte: Carte, quantityToRemove: Int = 1) {
        val key = carte.uniqueId()
        val existing = find(key) ?: return
        if (existing.quantity > quantityToRemove) {
            existing.quantity -= quantityToRemove
            update(key, existing)
        } else {
            delete(key)
        }
    }
}

