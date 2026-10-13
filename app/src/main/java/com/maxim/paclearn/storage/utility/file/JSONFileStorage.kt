package com.maxim.paclearn.storage.utility.file

import android.content.Context
import org.json.JSONObject

abstract class JSONFileStorage<T>(context: Context, name: String) :
    FileStorage<T>(context, name, "json") {

    protected abstract fun objectToJson(nom: String, obj: T): JSONObject
    protected abstract fun jsonToObject(json: JSONObject): T

    override fun dataToString(data: HashMap<String, T>): String {
        val json = JSONObject()
        data.forEach { (key, value) -> json.put(key, objectToJson(key, value)) }
        return json.toString()
    }

    override fun stringToData(value: String): HashMap<String, T> {
        val data = HashMap<String, T>()
        val json = JSONObject(value)
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            data[key] = jsonToObject(json.getJSONObject(key))
        }
        return data
    }
}

