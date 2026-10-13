package com.maxim.paclearn.storage

import android.content.Context

fun readJsonFromAssets(context: Context, fileName: String): String {
    return context.assets.open(fileName).bufferedReader().use { it.readText() }
}

fun loadJsonFromAssets(context: Context, fileName: String): String {
    val inputStream = context.assets.open(fileName)
    return inputStream.bufferedReader().use { it.readText() }
}

