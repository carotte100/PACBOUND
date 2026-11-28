package com.maxim.paclearn.controller.storage

import android.content.Context

/** Pour les tests : l'argent ajoute par le menu de triche, donne au debut de la prochaine partie. */
class Triche(context: Context) {

    private val prefs = context.getSharedPreferences("triche", Context.MODE_PRIVATE)

    var bonusArgent: Int
        get() = prefs.getInt(KEY_ARGENT, 0)
        set(valeur) = prefs.edit().putInt(KEY_ARGENT, valeur).apply()

    /** Rend le bonus et le remet a zero : il ne sert qu'une fois. */
    fun prendreBonus(): Int = bonusArgent.also { bonusArgent = 0 }

    private companion object {
        const val KEY_ARGENT = "argent"
    }
}
