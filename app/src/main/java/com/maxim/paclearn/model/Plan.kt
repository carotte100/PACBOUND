package com.maxim.paclearn.model

import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Les rues autour du joueur et le placement des pieces le long de ces rues.

private const val METRES_PAR_DEGRE = 111_320.0
private const val TROTTOIR_M = 2.0      // du bord de la chaussee au milieu du trottoir
private const val BORDURE_M = 0.5       // tout au bord, quand le trottoir est pris
private const val ECART_MIN_M = 25.0    // entre deux pieces de rues differentes
private const val TAILLE_CASE_M = 20.0

/** Une rue : son axe (tel qu'OSM le trace) et la demi-largeur de sa chaussee (0 = pietonne). */
class Rue(val points: List<GeoPoint>, val demiLargeur: Double)

class Zone(val rues: List<Rue>, val batiments: List<List<GeoPoint>>)

/** Un rectangle GPS, en degres. */
data class Cadre(val sud: Double, val ouest: Double, val nord: Double, val est: Double) {
    fun contient(p: GeoPoint) = p.latitude in sud..nord && p.longitude in ouest..est

    fun couvre(autre: Cadre) = autre.sud >= sud && autre.nord <= nord && autre.ouest >= ouest && autre.est <= est

    /** Distance (en degres) entre [p] et le centre du cadre : pour choisir le mieux centre. */
    fun ecartAuCentre(p: GeoPoint) = hypot(p.latitude - (sud + nord) / 2, p.longitude - (ouest + est) / 2)
}

fun Cadre.croise(autre: Cadre) = sud <= autre.nord && nord >= autre.sud && ouest <= autre.est && est >= autre.ouest

/** Le rectangle qui entoure tous les [points]. */
fun cadreDe(points: List<GeoPoint>) = Cadre(
    points.minOf { it.latitude }, points.minOf { it.longitude },
    points.maxOf { it.latitude }, points.maxOf { it.longitude }
)

// Le quartier est decoupe en tuiles d'environ 1,1 km, sur une grille fixe en degres :
// une partie ne relit que les tuiles autour du joueur, pas tout le quartier.
const val PAS_TUILE_LAT = 0.01
const val PAS_TUILE_LON = 0.014

data class Tuile(val i: Int, val j: Int) {
    val cadre get() = Cadre(i * PAS_TUILE_LAT, j * PAS_TUILE_LON, (i + 1) * PAS_TUILE_LAT, (j + 1) * PAS_TUILE_LON)
}

/** Les tuiles qui touchent [cadre]. */
fun tuilesDe(cadre: Cadre): List<Tuile> {
    val i0 = floor(cadre.sud / PAS_TUILE_LAT).toInt()
    val i1 = floor(cadre.nord / PAS_TUILE_LAT).toInt()
    val j0 = floor(cadre.ouest / PAS_TUILE_LON).toInt()
    val j1 = floor(cadre.est / PAS_TUILE_LON).toInt()
    return (i0..i1).flatMap { i -> (j0..j1).map { j -> Tuile(i, j) } }
}

/** Le rectangle qui entoure toutes les [tuiles]. */
fun cadreDesTuiles(tuiles: List<Tuile>) = Cadre(
    tuiles.minOf { it.i } * PAS_TUILE_LAT, tuiles.minOf { it.j } * PAS_TUILE_LON,
    (tuiles.maxOf { it.i } + 1) * PAS_TUILE_LAT, (tuiles.maxOf { it.j } + 1) * PAS_TUILE_LON
)

/** Range chaque rue et batiment dans toutes les tuiles qu'il touche. Chaque tuile a une entree, meme vide. */
fun decouper(zone: Zone, tuiles: List<Tuile>): Map<Tuile, Zone> = tuiles.associateWith { tuile ->
    val c = tuile.cadre
    Zone(zone.rues.filter { cadreDe(it.points).croise(c) }, zone.batiments.filter { cadreDe(it).croise(c) })
}

/** Reunit plusieurs tuiles : une rue a cheval sur deux tuiles n'est gardee qu'une fois. */
fun fusionner(zones: List<Zone>): Zone {
    fun cle(points: List<GeoPoint>) = "${points.first().latitude},${points.first().longitude},${points.last().latitude},${points.last().longitude},${points.size}"
    return Zone(
        zones.flatMap { it.rues }.distinctBy { cle(it.points) },
        zones.flatMap { it.batiments }.distinctBy { cle(it) }
    )
}

/** Le carre de [demiCote] metres autour de [centre]. */
fun cadreAutour(centre: GeoPoint, demiCote: Double): Cadre {
    val repere = RepereLocal(centre)
    val sudOuest = repere.versGeo(Vec(-demiCote, -demiCote))
    val nordEst = repere.versGeo(Vec(demiCote, demiCote))
    return Cadre(sudOuest.latitude, sudOuest.longitude, nordEst.latitude, nordEst.longitude)
}

/**
 * Sans plan (hors ligne, zone jamais enregistree) : une grille de pieces tous les
 * [espacement] metres, fixe dans le monde pour que les pieces ne bougent pas d'un rechargement a l'autre.
 */
fun placerPiecesSansPlan(centre: GeoPoint, demiCote: Double, espacement: Double): List<GeoPoint> {
    val repere = RepereLocal(centre)
    val pasLat = espacement / METRES_PAR_DEGRE
    val pasLon = espacement / (METRES_PAR_DEGRE * cos(Math.toRadians(centre.latitude)))
    val n = ceil(demiCote / espacement).toLong() + 1
    val i0 = floor(centre.latitude / pasLat).toLong()
    val j0 = floor(centre.longitude / pasLon).toLong()
    val pieces = ArrayList<GeoPoint>()
    for (i in i0 - n..i0 + n) {
        val decalage = if (i % 2 == 0L) 0.0 else 0.5 // une rangee sur deux en quinconce
        for (j in j0 - n..j0 + n) {
            val p = GeoPoint(i * pasLat, (j + decalage) * pasLon)
            if (repere.versXY(p).estDans(demiCote)) pieces.add(p)
        }
    }
    return pieces
}

/** La case d'environ 10 m qui contient [point] : sert a reconnaitre une piece deja ramassee. */
fun celluleDe(point: GeoPoint): Long =
    (Math.round(point.latitude * 1e4) shl 32) or (Math.round(point.longitude * 1e4) and 0xFFFFFFFFL)

/**
 * Pose une piece tous les [espacement] metres le long des rues, sur le trottoir
 * (alternativement a gauche et a droite), hors des batiments et des chaussees.
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
            val cote = if (n++ % 2 == 0) 1.0 else -1.0
            // (-dirY, dirX) pointe a gauche du sens de la rue.
            val place = decalages.asSequence()
                .map { Vec(arret.x - dirY * it * cote, arret.y + dirX * it * cote) }
                .firstOrNull { p ->
                    p.estDans(demiCote) && obstacles.estLibre(p) &&
                        dejaPosees.pres(p).none { it.distance(p) < ECART_MIN_M }
                }
            if (place != null) {
                dejaPosees.ajouter(place, place.x - ECART_MIN_M, place.y - ECART_MIN_M, place.x + ECART_MIN_M, place.y + ECART_MIN_M)
                pieces.add(repere.versGeo(place))
            }
        }
    }
    return pieces
}

/** Appelle [arret] tous les [espacement] metres le long de la ligne, avec sa direction. */
private inline fun parcourir(points: List<Vec>, espacement: Double, arret: (point: Vec, dirX: Double, dirY: Double) -> Unit) {
    var reste = 0.0 // reporte d'un segment au suivant, pour garder l'ecart dans les virages
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

/** Les chaussees et les batiments : la ou une piece ne doit pas tomber. */
private class Obstacles(zone: Zone, repere: RepereLocal, demiCote: Double) {
    private class Troncon(val a: Vec, val b: Vec, val demiLargeur: Double)

    private val troncons = Grille<Troncon>(demiCote)
    private val batiments = Grille<List<Vec>>(demiCote)

    init {
        for (rue in zone.rues) {
            val d = rue.demiLargeur
            if (d == 0.0) continue
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
        val t = if (longueurCarre == 0.0) 0.0
        else (((p.x - a.x) * dx + (p.y - a.y) * dy) / longueurCarre).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Lancer de rayon : un nombre impair de murs traverses = le point est dedans. */
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

/** Range des objets par cases de [TAILLE_CASE_M] m, pour ne tester que les voisins d'un point. */
private class Grille<T>(demiCote: Double) {
    private val limite = floor(demiCote / TAILLE_CASE_M).toInt() + 1
    private val cases = HashMap<Long, MutableList<T>>()

    fun ajouter(objet: T, minX: Double, minY: Double, maxX: Double, maxY: Double) {
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

/** Coordonnees en metres autour d'un point : x vers l'est, y vers le nord. */
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
