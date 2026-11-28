package com.maxim.paclearn.controller.storage

import android.content.Context
import com.maxim.paclearn.controller.storage.utility.file.JSONFileStorage
import com.maxim.paclearn.model.Carte
import org.json.JSONObject

/** La collection du joueur, enregistree dans files/storage_collection.json. */
class CarteStorage(context: Context) : JSONFileStorage<Carte>(context, "collection") {

    override fun create(nom: String, obj: Carte): Carte = obj.copy()

    override fun objectToJson(nom: String, obj: Carte): JSONObject = obj.toCollectionJson()

    override fun jsonToObject(json: JSONObject): Carte = Carte.fromJson(json)

    fun addOrIncrement(carte: Carte, additionalQuantity: Int = 1) {
        val key = carte.uniqueId()
        val existing = find(key)
        if (existing != null) {
            existing.quantity += additionalQuantity
            update(key, existing)
        } else {
            insert(key, carte.copy(quantity = additionalQuantity))
        }
    }

    fun getCollection(): List<Carte> = findAll()

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
