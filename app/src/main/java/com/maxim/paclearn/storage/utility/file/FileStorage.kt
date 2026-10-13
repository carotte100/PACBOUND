package com.maxim.paclearn.storage.utility.file

import android.content.Context
import com.maxim.paclearn.storage.utility.Storage
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

abstract class FileStorage<T>(private val context: Context, name: String, extension: String) :
    Storage<T> {

    private val fileName = "storage_$name.$extension"
    private var data: HashMap<String, T> = HashMap()

    init {
        read()
    }

    protected abstract fun create(nom: String, obj: T): T
    protected abstract fun dataToString(data: HashMap<String, T>): String
    protected abstract fun stringToData(value: String): HashMap<String, T>

    private fun read() {
        try {
            val input = context.openFileInput(fileName)
            if (input != null) {
                val builder = StringBuilder()
                val bufferedReader = BufferedReader(InputStreamReader(input))
                var temp = bufferedReader.readLine()
                while (temp != null) {
                    builder.append(temp)
                    temp = bufferedReader.readLine()
                }
                input.close()
                data = stringToData(builder.toString())
            }
        } catch (e: FileNotFoundException) {
            data = HashMap()
        }
    }

    private fun write() {
        val output = context.openFileOutput(fileName, Context.MODE_PRIVATE)
        val writer = OutputStreamWriter(output)
        writer.write(dataToString(data))
        writer.close()
    }

    override fun insert(key: String, obj: T) {
        data[key] = obj
        write()
    }

    override fun size(): Int {
        return data.size
    }

    override fun find(key: String): T? {
        return data[key]
    }

    override fun findAll(): List<T> {
        return data.toList().map { pair -> pair.second }
    }

    override fun update(key: String, obj: T) {
        data[key] = obj
        write()
    }

    override fun delete(key: String) {
        data.remove(key)
        write()
    }
}

