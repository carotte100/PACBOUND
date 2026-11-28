package com.maxim.paclearn.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.maxim.paclearn.R
import com.maxim.paclearn.model.RepereLocal
import com.maxim.paclearn.model.Rue
import com.maxim.paclearn.model.Zone
import kotlin.math.max
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

// Le dessin du plan. Chaque calque (Overlay osmdroid) est redessine a chaque image :
// les rues, puis les pieces, puis le joueur.

/** Un pinceau de la couleur [couleur] de colors.xml. */
private fun pinceau(context: Context, couleur: Int) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, couleur) }

/** Les rues et batiments de la zone chargee, dessines a partir des donnees OSM. */
class CalqueRues(zone: Zone, centre: GeoPoint, context: Context) : Overlay() {

    private val repere = RepereLocal(centre)
    private val densite = context.resources.displayMetrics.density

    // Bouts ronds : les segments d'une meme rue se raccordent sans cassure.
    private val bordRue = pinceau(context, R.color.plan_rue_bord).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rue = pinceau(context, R.color.plan_rue).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val batiment = pinceau(context, R.color.plan_batiment)
    private val bordBatiment = pinceau(context, R.color.plan_batiment_bord).apply { style = Paint.Style.STROKE; strokeWidth = densite }

    // Converties une fois en metres ; groupees par largeur (une epaisseur par drawLines).
    private val groupes = zone.rues
        .groupBy { if (it.demiLargeur == 0.0) LARGEUR_PIETONNE_M else it.demiLargeur * 2 }
        .map { (largeur, rues) -> GroupeDeRues(largeur.toFloat(), rues.flatMap { segmentsEnMetres(it) }.toFloatArray()) }
    private val batiments = zone.batiments.map { contour ->
        Batiment(contour.map(repere::versXY).flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray())
    }

    // Reutilises a chaque image : pas de nouvel objet pendant que le plan bouge.
    private val formeBatiments = Path()
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        projection.toPixels(repere.origine, point)
        val cx = point.x.toFloat()
        val cy = point.y.toFloat()
        val echelle = projection.metersToPixels(100f, repere.origine.latitude, projection.zoomLevel) / 100f

        // Seuls les batiments visibles : un trop grand dessin plante les vieux GPU.
        val ecran = RectF(-cx / echelle, (cy - canvas.height) / echelle, (canvas.width - cx) / echelle, cy / echelle)
        formeBatiments.rewind()
        for (b in batiments) {
            if (!RectF.intersects(b.cadre, ecran)) continue
            val m = b.metres
            formeBatiments.moveTo(cx + m[0] * echelle, cy - m[1] * echelle)
            for (i in 2 until m.size step 2) formeBatiments.lineTo(cx + m[i] * echelle, cy - m[i + 1] * echelle)
            formeBatiments.close()
        }
        canvas.drawPath(formeBatiments, batiment)
        canvas.drawPath(formeBatiments, bordBatiment)

        for (groupe in groupes) groupe.versPixels(cx, cy, echelle)
        // Tous les bords d'abord, puis le gris : les carrefours se raccordent proprement.
        for (groupe in groupes) {
            bordRue.strokeWidth = largeurPx(groupe, echelle) + 3 * densite
            canvas.drawLines(groupe.pixels, bordRue)
        }
        for (groupe in groupes) {
            rue.strokeWidth = largeurPx(groupe, echelle)
            canvas.drawLines(groupe.pixels, rue)
        }
    }

    // Au moins 3 dp, pour qu'une ruelle reste visible.
    private fun largeurPx(groupe: GroupeDeRues, echelle: Float) = max(3 * densite, groupe.largeurM * echelle)

    /** Les segments d'une rue, en metres : [x1, y1, x2, y2, x2, y2, x3, y3...]. */
    private fun segmentsEnMetres(rue: Rue): List<Float> {
        val points = rue.points.map(repere::versXY)
        return (0 until points.size - 1).flatMap { i ->
            listOf(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y).map { it.toFloat() }
        }
    }

    /** Le contour d'un batiment en metres [x1, y1, x2, y2...], et le rectangle qui l'entoure. */
    private class Batiment(val metres: FloatArray) {
        val cadre = RectF(
            (metres.indices step 2).minOf { metres[it] }, (1 until metres.size step 2).minOf { metres[it] },
            (metres.indices step 2).maxOf { metres[it] }, (1 until metres.size step 2).maxOf { metres[it] }
        )
    }

    private class GroupeDeRues(val largeurM: Float, private val metres: FloatArray) {
        val pixels = FloatArray(metres.size)

        fun versPixels(cx: Float, cy: Float, echelle: Float) {
            for (i in metres.indices step 2) {
                pixels[i] = cx + metres[i] * echelle
                // En metres, y monte vers le nord ; a l'ecran, il descend.
                pixels[i + 1] = cy - metres[i + 1] * echelle
            }
        }
    }

    private companion object {
        const val LARGEUR_PIETONNE_M = 4.0 // OSM ne donne pas la largeur des rues pietonnes
    }
}

/** Les pieces a ramasser : un disque dore cercle de blanc. */
class CalquePieces(pieces: List<GeoPoint>, context: Context) : Overlay() {

    private val pieces = pieces.toMutableList() // les pieces ramassees en sont retirees
    private val rayon = 4.5f * context.resources.displayMetrics.density
    private val bord = pinceau(context, R.color.piece_bord)
    private val or = pinceau(context, R.color.piece)
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        for (piece in pieces) {
            projection.toPixels(piece, point)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), rayon * 1.4f, bord)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), rayon, or)
        }
    }

    /** Retire les pieces a moins de [rayonM] metres de [position], et les renvoie. */
    fun ramasser(position: GeoPoint, rayonM: Double): List<GeoPoint> {
        val proches = pieces.filter { position.distanceToAsDouble(it) <= rayonM }
        pieces.removeAll(proches)
        return proches
    }
}

/** Le joueur : sa zone de ramassage, son regard (oriente par la boussole) et son smiley. */
class CalqueJoueur(context: Context, private val rayonRamassageM: Double) : Overlay() {

    var position: GeoPoint? = null
    var cap = 0f // en degres, 0 = nord

    private val densite = context.resources.displayMetrics.density
    private val rayonRegard = 75 * densite
    private val rayonVisage = 14 * densite

    private val zone = pinceau(context, R.color.joueur_zone)
    private val bordZone = pinceau(context, R.color.joueur_zone_bord).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * densite
        pathEffect = DashPathEffect(floatArrayOf(6 * densite, 4 * densite), 0f) // pointilles
    }
    private val regard = pinceau(context, R.color.joueur_regard)
    private val contour = pinceau(context, R.color.joueur_contour)
    private val visage = pinceau(context, R.color.joueur_visage)
    private val traits = pinceau(context, R.color.joueur_traits)
    private val sourire = pinceau(context, R.color.joueur_traits).apply {
        style = Paint.Style.STROKE
        strokeWidth = rayonVisage * 0.16f
        strokeCap = Paint.Cap.ROUND
    }

    private val cadre = RectF()
    private val formeRegard = Path()
    private val point = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        val ici = position ?: return
        projection.toPixels(ici, point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()

        // Zone de ramassage a sa vraie taille en metres.
        val rayonZone = projection.metersToPixels(rayonRamassageM.toFloat(), ici.latitude, projection.zoomLevel)
        canvas.drawCircle(x, y, rayonZone, zone)
        canvas.drawCircle(x, y, rayonZone, bordZone)

        // Le regard, tourne vers le cap (arcTo part de l'est, la boussole du nord : -90).
        cadre.set(x - rayonRegard, y - rayonRegard, x + rayonRegard, y + rayonRegard)
        formeRegard.rewind()
        formeRegard.moveTo(x, y)
        formeRegard.arcTo(cadre, cap - 90f - DEMI_ANGLE_REGARD, DEMI_ANGLE_REGARD * 2)
        formeRegard.close()
        canvas.drawPath(formeRegard, regard)

        // Le smiley reste droit : c'est le regard qui indique la direction.
        val r = rayonVisage
        canvas.drawCircle(x, y, r * 1.18f, contour)
        canvas.drawCircle(x, y, r, visage)
        for (oeilX in floatArrayOf(x - r * 0.34f, x + r * 0.34f)) {
            canvas.drawOval(oeilX - r * 0.12f, y - r * 0.42f, oeilX + r * 0.12f, y - r * 0.02f, traits)
        }
        canvas.drawArc(x - r * 0.55f, y - r * 0.35f, x + r * 0.55f, y + r * 0.55f, 25f, 130f, false, sourire)
    }

    private companion object {
        const val DEMI_ANGLE_REGARD = 28f
    }
}
