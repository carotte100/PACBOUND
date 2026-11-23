package com.maxim.paclearn

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Les donnees du plan : les rues et batiments autour du joueur, telecharges depuis
// OpenStreetMap, puis les pieces posees le long des rues.

// Overpass est le service qui interroge OpenStreetMap. Ses serveurs publics tombent
// souvent : on les essaie l'un apres l'autre, le plus rapide aux essais en premier.
private val SERVEURS_OVERPASS = listOf(
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter"
)
// Les types de rues ou poser des pieces. Pas de trottoirs ni de chemins : OSM les
// trace en double de chaque rue, ce qui noierait le plan sous les pieces.
private val RUES_PRATICABLES = listOf(
    "primary", "secondary", "tertiary", "residential", "unclassified",
    "living_street", "pedestrian"
)

private const val METRES_PAR_DEGRE = 111_320.0
private const val LARGEUR_VOIE_M = 3.0
private const val TROTTOIR_M = 2.0      // du bord de la chaussee au milieu du trottoir
private const val BORDURE_M = 0.5       // tout au bord, quand le trottoir est pris
private const val ECART_MIN_M = 25.0    // entre deux pieces de rues differentes
private const val TAILLE_CASE_M = 20.0

/** Une rue : son axe tel qu'OSM le trace (une ligne au milieu) et la demi-largeur de sa chaussee. */
class Rue(val points: List<GeoPoint>, val demiLargeur: Double)

/** Ce qu'il faut connaitre d'une zone pour la dessiner et y poser les pieces. */
class Zone(val rues: List<Rue>, val batiments: List<List<GeoPoint>>)

/** Telecharge les rues et batiments du carre de [demiCote] metres autour de [centre]. */
fun telechargerZone(centre: GeoPoint, demiCote: Double, userAgent: String): Zone {
    val repere = RepereLocal(centre)
    val sudOuest = repere.versGeo(Vec(-demiCote, -demiCote))
    val nordEst = repere.versGeo(Vec(demiCote, demiCote))
    val carre = "${sudOuest.latitude},${sudOuest.longitude},${nordEst.latitude},${nordEst.longitude}"

    // La requete, dans le langage d'Overpass. Les batiments sortent sans leurs
    // details (skel) : seule leur forme sert, et c'est ce qui les distingue des rues.
    val types = RUES_PRATICABLES.joinToString("\n") { """way["highway"="$it"]($carre);""" }
    val requete = """
        [out:json][timeout:25];
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
            return lireZone(envoyer(serveur, requete, userAgent))
        } catch (e: Exception) {
            erreur = e
        }
    }
    throw erreur ?: IOException("Aucun serveur Overpass joignable")
}

/** Envoie la requete au serveur et renvoie sa reponse (du texte JSON). */
private fun envoyer(serveur: String, requete: String, userAgent: String): String {
    val connexion = (URL(serveur).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 15_000
        readTimeout = 60_000 // un serveur charge met jusqu'a ~40 s a repondre
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

/** Transforme la reponse JSON en rues et batiments. */
private fun lireZone(json: String): Zone {
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

/**
 * La demi-largeur de la chaussee : la largeur si OSM la donne, sinon le nombre
 * de voies, sinon une valeur typique du type de rue.
 */
private fun demiLargeurDe(details: JSONObject): Double {
    val type = details.optString("highway")
    // Rue pietonne : on marche au milieu, pas de bord a chercher.
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

/**
 * Le numero de la case d'environ 10 m qui contient [point]. Une rue rechargee
 * repose ses pieces aux memes endroits, donc dans les memes cases : c'est ce qui
 * permet de reconnaitre une piece deja ramassee.
 */
fun celluleDe(point: GeoPoint): Long =
    (Math.round(point.latitude * 1e4) shl 32) or (Math.round(point.longitude * 1e4) and 0xFFFFFFFFL)

/**
 * Pose une piece tous les [espacement] metres le long des rues, dans le carre de
 * [demiCote] metres autour de [centre].
 *
 * OSM ne connait que l'axe des rues : une piece posee dessus tomberait au milieu
 * de la chaussee. On la decale donc sur le trottoir, d'un cote puis de l'autre, et
 * on essaie plusieurs places jusqu'a en trouver une qui ne soit ni dans un batiment,
 * ni sur une chaussee, ni trop pres d'une autre piece. Sinon, pas de piece.
 */
fun placerPieces(zone: Zone, centre: GeoPoint, demiCote: Double, espacement: Double): List<GeoPoint> {
    val repere = RepereLocal(centre)
    val obstacles = Obstacles(zone, repere, demiCote)
    val dejaPosees = Grille<Vec>(demiCote)
    val pieces = ArrayList<GeoPoint>()

    for (rue in zone.rues) {
        // Les places essayees, en metres depuis l'axe : le trottoir, puis le bord de la chaussee.
        val d = rue.demiLargeur
        val decalages = if (d == 0.0) listOf(0.0)
        else listOf(d + TROTTOIR_M, -(d + TROTTOIR_M), d + BORDURE_M, -(d + BORDURE_M))
        var n = 0
        parcourir(rue.points.map(repere::versXY), espacement) { arret, dirX, dirY ->
            val cote = if (n++ % 2 == 0) 1.0 else -1.0 // une piece a gauche, la suivante a droite
            // (-dirY, dirX) pointe a gauche du sens de la rue.
            val place = decalages.asSequence()
                .map { Vec(arret.x - dirY * it * cote, arret.y + dirX * it * cote) }
                .firstOrNull { p ->
                    p.estDans(demiCote) && obstacles.estLibre(p) &&
                        dejaPosees.pres(p).none { it.distance(p) < ECART_MIN_M }
                }
            if (place != null) {
                // Rangee dans toutes les cases a moins de ECART_MIN_M, pour qu'une
                // voisine la trouve en ne regardant que sa propre case.
                dejaPosees.ajouter(place, place.x - ECART_MIN_M, place.y - ECART_MIN_M, place.x + ECART_MIN_M, place.y + ECART_MIN_M)
                pieces.add(repere.versGeo(place))
            }
        }
    }
    return pieces
}

/** Appelle [arret] tous les [espacement] metres le long de la ligne, avec sa direction. */
private inline fun parcourir(points: List<Vec>, espacement: Double, arret: (point: Vec, dirX: Double, dirY: Double) -> Unit) {
    // Ce qui reste depuis le dernier arret passe au segment suivant : dans un virage,
    // l'ecart reste de [espacement] metres le long de la rue.
    var reste = 0.0
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val longueur = a.distance(b)
        if (longueur == 0.0) continue
        val dirX = (b.x - a.x) / longueur
        val dirY = (b.y - a.y) / longueur
        var position = espacement - reste
        while (position <= longueur) {
            arret(Vec(a.x + dirX * position, a.y + dirY * position), dirX, dirY)
            position += espacement
        }
        reste = longueur - (position - espacement)
    }
}

/** Les chaussees et les batiments de la zone : la ou une piece ne doit pas tomber. */
private class Obstacles(zone: Zone, repere: RepereLocal, demiCote: Double) {
    /** Un bout de chaussee : un segment de l'axe, elargi de chaque cote. */
    private class Troncon(val a: Vec, val b: Vec, val demiLargeur: Double)

    private val troncons = Grille<Troncon>(demiCote)
    private val batiments = Grille<List<Vec>>(demiCote)

    init {
        for (rue in zone.rues) {
            val d = rue.demiLargeur
            if (d == 0.0) continue // rue pietonne : on peut s'y tenir
            val points = rue.points.map(repere::versXY)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                troncons.ajouter(Troncon(a, b, d), min(a.x, b.x) - d, min(a.y, b.y) - d, max(a.x, b.x) + d, max(a.y, b.y) + d)
            }
        }
        for (contour in zone.batiments) {
            val polygone = contour.map(repere::versXY)
            batiments.ajouter(
                polygone,
                polygone.minOf { it.x }, polygone.minOf { it.y }, polygone.maxOf { it.x }, polygone.maxOf { it.y }
            )
        }
    }

    fun estLibre(p: Vec) =
        troncons.pres(p).none { distanceAuSegment(p, it.a, it.b) < it.demiLargeur } &&
            batiments.pres(p).none { estDedans(it, p) }

    private fun distanceAuSegment(p: Vec, a: Vec, b: Vec): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val longueurCarre = dx * dx + dy * dy
        // On projette p sur le segment, sans depasser ses extremites.
        val t = if (longueurCarre == 0.0) 0.0
        else (((p.x - a.x) * dx + (p.y - a.y) * dy) / longueurCarre).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /**
     * Lancer de rayon : on part du point vers l'est et on compte les murs du
     * batiment traverses. Un nombre impair veut dire que le point est dedans.
     */
    private fun estDedans(polygone: List<Vec>, p: Vec): Boolean {
        var dedans = false
        var j = polygone.size - 1
        for (i in polygone.indices) {
            val a = polygone[i]
            val b = polygone[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
                dedans = !dedans
            }
            j = i
        }
        return dedans
    }
}

/**
 * Range des objets par cases de [TAILLE_CASE_M] metres : pour un point, on ne
 * teste que les objets de sa case, au lieu de tous ceux de la zone.
 */
private class Grille<T>(demiCote: Double) {
    private val limite = floor(demiCote / TAILLE_CASE_M).toInt() + 1
    private val cases = HashMap<Long, MutableList<T>>()

    fun ajouter(objet: T, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        // Les rues debordent souvent de la zone : on ne range que ce qui la touche.
        for (cx in case(minX).coerceAtLeast(-limite)..case(maxX).coerceAtMost(limite)) {
            for (cy in case(minY).coerceAtLeast(-limite)..case(maxY).coerceAtMost(limite)) {
                cases.getOrPut(cle(cx, cy)) { ArrayList() }.add(objet)
            }
        }
    }

    fun pres(p: Vec): List<T> = cases[cle(case(p.x), case(p.y))] ?: emptyList()

    private fun case(v: Double) = floor(v / TAILLE_CASE_M).toInt()
    private fun cle(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
}

/** Un point en metres sur le repere local. */
internal class Vec(val x: Double, val y: Double) {
    fun estDans(demiCote: Double) = abs(x) <= demiCote && abs(y) <= demiCote
    fun distance(autre: Vec) = hypot(x - autre.x, y - autre.y)
}

/**
 * Des coordonnees en metres autour d'un point : x vers l'est, y vers le nord. A
 * l'echelle d'un quartier, la Terre est plate : les distances deviennent simples.
 */
internal class RepereLocal(val origine: GeoPoint) {
    private val metresParDegreLon = METRES_PAR_DEGRE * cos(Math.toRadians(origine.latitude))

    fun versXY(p: GeoPoint) = Vec(
        (p.longitude - origine.longitude) * metresParDegreLon,
        (p.latitude - origine.latitude) * METRES_PAR_DEGRE
    )

    fun versGeo(v: Vec) = GeoPoint(
        origine.latitude + v.y / METRES_PAR_DEGRE,
        origine.longitude + v.x / metresParDegreLon
    )
}
