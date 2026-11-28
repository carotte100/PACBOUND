package com.maxim.paclearn.model

/** L'effet visuel d'une carte de la collection, selon le nombre d'exemplaires possedes. */
enum class Eclat(val seuil: Int) {
    AUCUN(0),
    BRILLANTE(5),
    HOLOGRAPHIQUE(10),
    LEGENDAIRE(20);

    /** Le palier suivant, ou null au dernier. */
    val suivant: Eclat? get() = entries.getOrNull(ordinal + 1)
}

fun eclatDe(quantite: Int): Eclat = Eclat.entries.last { quantite >= it.seuil }
