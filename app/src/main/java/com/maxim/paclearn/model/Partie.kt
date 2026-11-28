package com.maxim.paclearn.model

/** Ce que rapporte une piece ramassee, en euros. */
const val VALEUR_PIECE = 2

/** Le bilan d'une partie terminee. */
data class RecapPartie(
    val secondesJouees: Int,
    val pieces: Int,
    val argentRestant: Int, // perdu : l'argent ne survit pas a la partie
    val cartes: List<Carte>,
    val ameliorations: List<Amelioration>,
    val tempsEcoule: Boolean,
    val argentDepart: Int = 0 // 0 sauf triche
) {
    val argentGagne get() = pieces * VALEUR_PIECE
    val argentDepense get() = argentDepart + argentGagne - argentRestant
}
