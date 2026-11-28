package com.maxim.paclearn.controller.storage

import android.content.Context

fun readJsonFromAssets(context: Context, fileName: String): String =
    context.assets.open(fileName).bufferedReader().use { it.readText() }
