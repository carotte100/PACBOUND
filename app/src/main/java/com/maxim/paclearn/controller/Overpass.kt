package com.maxim.paclearn.controller

import com.maxim.paclearn.model.Cadre
import com.maxim.paclearn.model.Rue
import com.maxim.paclearn.model.Zone
import com.maxim.paclearn.model.cadreAutour
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Telechargement des rues et batiments depuis OpenStreetMap (service Overpass).

// Les serveurs publics tombent souvent : on les essaie l'un apres l'autre.
private val SERVEURS_OVERPASS = listOf(
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter"
)
// Pas de trottoirs ni de chemins : OSM les trace en double de chaque rue.
private val RUES_PRATICABLES = listOf(
    "primary", "secondary", "tertiary", "residential", "unclassified",
    "living_street", "pedestrian"
)
private const val LARGEUR_VOIE_M = 3.0

/** Telecharge les rues et batiments du carre de [demiCote] metres autour de [centre]. */
fun telechargerZone(centre: GeoPoint, demiCote: Double, userAgent: String): Zone =
    lireZone(telechargerJson(cadreAutour(centre, demiCote), userAgent))

/**
 * La reponse brute d'Overpass pour [cadre], a enregistrer telle quelle sur le telephone.
 * [delaiS] : le temps laisse au serveur (plus long pour un quartier entier).
 */
fun telechargerJson(cadre: Cadre, userAgent: String, delaiS: Int = 25): String {
    val carre = "${cadre.sud},${cadre.ouest},${cadre.nord},${cadre.est}"
    // Les batiments sortent sans tags (skel) : c'est ce qui les distingue des rues dans lireZone.
    val types = RUES_PRATICABLES.joinToString("\n") { """way["highway"="$it"]($carre);""" }
    val requete = """
        [out:json][timeout:$delaiS];
        (
        $types
        );
        out geom;
        way["building"]($carre);
        out skel geom;
    """.trimIndent()

    var erreur: Exception? = null
    for (serveur in SERVEURS_OVERPASS) {
        try {
            return envoyer(serveur, requete, userAgent, (delaiS + 35) * 1000)
        } catch (e: Exception) {
            erreur = e
        }
    }
    throw erreur ?: IOException("Aucun serveur Overpass joignable")
}

private fun envoyer(serveur: String, requete: String, userAgent: String, delaiLectureMs: Int): String {
    val connexion = (URL(serveur).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = delaiLectureMs
        doOutput = true
        setRequestProperty("User-Agent", userAgent)
    }
    try {
        connexion.outputStream.use { it.write("data=${URLEncoder.encode(requete, "UTF-8")}".toByteArray()) }
        if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Overpass ${connexion.responseCode} sur $serveur")
        }
        return connexion.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connexion.disconnect()
    }
}

fun lireZone(json: String): Zone {
    val rues = ArrayList<Rue>()
    val batiments = ArrayList<List<GeoPoint>>()
    val elements = JSONObject(json).getJSONArray("elements")
    for (i in 0 until elements.length()) {
        val element = elements.getJSONObject(i)
        val geometrie = element.optJSONArray("geometry") ?: continue
        val points = (0 until geometrie.length()).map { j ->
            val noeud = geometrie.getJSONObject(j)
            GeoPoint(noeud.getDouble("lat"), noeud.getDouble("lon"))
        }
        val details = element.optJSONObject("tags")
        when {
            details != null && points.size >= 2 -> rues.add(Rue(points, demiLargeurDe(details)))
            details == null && points.size >= 3 -> batiments.add(points)
        }
    }
    return Zone(rues, batiments)
}

/** La largeur si OSM la donne, sinon le nombre de voies, sinon une valeur typique. */
private fun demiLargeurDe(details: JSONObject): Double {
    val type = details.optString("highway")
    if (type == "pedestrian" || type == "living_street") return 0.0

    // "width" s'ecrit parfois "6 m" : on ne garde que le nombre en tete.
    details.optString("width").takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()?.let { return it / 2 }
    details.optString("lanes").toIntOrNull()?.let { return it * LARGEUR_VOIE_M / 2 }
    return when (type) {
        "primary" -> 5.0
        "secondary" -> 4.5
        "tertiary" -> 4.0
        else -> 3.0
    }
}
