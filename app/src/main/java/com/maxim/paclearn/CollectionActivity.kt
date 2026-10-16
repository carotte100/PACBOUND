package com.maxim.paclearn

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.storage.CarteStorage
import com.maxim.paclearn.storage.Catalogue

// Palette reprise de la galerie de cartes du site Riftbound.
internal val RiftBlue = Color(0xFF013951)
internal val RiftDeep = Color(0xFF012A3D)
internal val RiftLine = Color(0xFF336073)
internal val RiftOrange = Color(0xFFF28C1B)
internal val Muted = Color.White.copy(alpha = 0.6f)

// Pas de police Beaufort embarquee : un serif epais en majuscules s'en approche.
internal val Title = FontFamily.Serif

private enum class Tri(val label: String) { COUT("Coût"), NOM("Nom"), RARETE("Rareté") }

internal val COULEURS = listOf(
    "rouge" to Color(0xFFE0443A),
    "vert" to Color(0xFF3FA34D),
    "bleu" to Color(0xFF2F7FD8),
    "orange" to Color(0xFFF28C1B),
    "pourpre" to Color(0xFF9146C8),
    "jaune" to Color(0xFFF2C94C)
)

internal val TYPES = listOf("unite" to "Unité", "sort" to "Sort", "equipement" to "Équipement")

internal val RARETES = listOf("commune", "peu commune", "rare", "epique")

private fun couleurDe(carte: Carte) =
    COULEURS.firstOrNull { it.first == carte.couleur.lowercase() }?.second ?: RiftLine

class CollectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(TRANSPARENT)
        )
        setContent {
            PacTheme {
                CollectionScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun CollectionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val storage = remember { CarteStorage(context) }
    // Tout le catalogue, comme un album : quantity = 0 pour les cartes pas encore achetees.
    val cartes = remember { mutableStateListOf<Carte>() }

    var filtresOuverts by remember { mutableStateOf(false) }
    var tri by remember { mutableStateOf(Tri.COUT) }
    var couleur by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf<String?>(null) }
    var recherche by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Carte?>(null) }

    LaunchedEffect(Unit) {
        val possedees = storage.getCollection().associateBy { it.uniqueId() }
        val catalogue = Catalogue.load(context).cartes
            .map { it.copy(quantity = possedees[it.uniqueId()]?.quantity ?: 0) }
        // Une carte possedee peut ne plus etre au catalogue : on la garde quand meme.
        val horsCatalogue = possedees.values.filter { c -> catalogue.none { it.uniqueId() == c.uniqueId() } }
        cartes.addAll(catalogue + horsCatalogue)
    }
    val obtenues = cartes.filter { it.quantity > 0 }

    val visibles = cartes.filter { carte ->
        (couleur == null || carte.couleur.equals(couleur, ignoreCase = true)) &&
            (type == null || carte.type.equals(type, ignoreCase = true)) &&
            (recherche.isBlank() || carte.nom.contains(recherche, ignoreCase = true) ||
                carte.tag.contains(recherche, ignoreCase = true))
    }
    val parSet = visibles
        .sortedWith(
            when (tri) {
                Tri.COUT -> compareBy<Carte>({ it.cost }, { it.nom })
                Tri.NOM -> compareBy { it.nom }
                Tri.RARETE -> compareByDescending<Carte> { RARETES.indexOf(it.rarete.lowercase()) }.thenBy { it.nom }
            }
        )
        .groupBy { it.set.lowercase() }
        .toSortedMap()

    Box(
        Modifier
            .fillMaxSize()
            .background(RiftBlue)
            .systemBarsPadding()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(
                        Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .border(1.dp, RiftLine)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("←", color = Color.White, fontSize = 20.sp)
                        }
                    }
                    Text(
                        "COLLECTION",
                        color = Color.White,
                        fontFamily = Title,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        "${obtenues.size} / ${cartes.size} cartes · ${obtenues.sumOf { it.quantity }} exemplaires",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .height(48.dp)
                                .background(if (filtresOuverts) RiftLine.copy(alpha = 0.45f) else Color.Transparent)
                                .border(1.dp, RiftLine)
                                .clickable { filtresOuverts = !filtresOuverts }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (filtresOuverts) "Masquer les filtres" else "Filtres",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        ChampRecherche(recherche, { recherche = it }, Modifier.weight(1f))
                    }

                    AnimatedVisibility(filtresOuverts) {
                        PanneauFiltres(
                            tri = tri, onTri = { tri = it },
                            couleur = couleur, onCouleur = { couleur = if (couleur == it) null else it },
                            type = type, onType = { type = if (type == it) null else it }
                        )
                    }
                }
            }

            if (parSet.isEmpty() && cartes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Aucune carte ne correspond à ces filtres.",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 40.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            parSet.forEach { (set, cartesDuSet) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "set_$set") {
                    Row(
                        Modifier.padding(top = 16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            set.uppercase(),
                            color = Color.White,
                            fontFamily = Title,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${cartesDuSet.count { it.quantity > 0 }} / ${cartesDuSet.size}",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
                items(cartesDuSet, key = { it.uniqueId() }) { carte ->
                    // Pas encore achetee : grisee, on voit seulement ce qu'il reste a trouver.
                    CarteVierge(
                        carte,
                        Modifier
                            .alpha(if (carte.quantity > 0) 1f else 0.3f)
                            .clickable { detail = carte }
                    )
                }
            }
        }

        detail?.let { carte ->
            FicheCarte(carte = carte, onDismiss = { detail = null })
        }
    }
}

@Composable
private fun ChampRecherche(valeur: String, onChange: (String) -> Unit, modifier: Modifier) {
    BasicTextField(
        value = valeur,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = SolidColor(RiftOrange),
        modifier = modifier
            .height(48.dp)
            .border(1.dp, RiftLine),
        decorationBox = { champ ->
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = Muted, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Box {
                    if (valeur.isEmpty()) Text("Rechercher", color = Muted, fontSize = 14.sp)
                    champ()
                }
            }
        }
    )
}

@Composable
private fun PanneauFiltres(
    tri: Tri, onTri: (Tri) -> Unit,
    couleur: String?, onCouleur: (String) -> Unit,
    type: String?, onType: (String) -> Unit
) {
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .border(1.dp, RiftLine)
            .background(RiftDeep)
    ) {
        SectionFiltre("Trier par") {
            Tri.values().forEach { Option(it.label, tri == it) { onTri(it) } }
        }
        Ligne()
        SectionFiltre("Domaine") {
            COULEURS.forEach { (nom, teinte) ->
                Option(nom.replaceFirstChar { it.uppercase() }, couleur == nom, teinte) { onCouleur(nom) }
            }
        }
        Ligne()
        SectionFiltre("Type") {
            TYPES.forEach { (cle, label) -> Option(label, type == cle) { onType(cle) } }
        }
    }
}

@Composable
private fun SectionFiltre(titre: String, options: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            titre,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { options() }
    }
}

@Composable
private fun Ligne() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(RiftLine)
    )
}

@Composable
private fun Option(label: String, actif: Boolean, pastille: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (actif) Color.White else Color.Transparent)
            .border(1.dp, if (actif) Color.White else RiftLine)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pastille != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(pastille, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (actif) RiftBlue else Color.White,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

/** Le visuel de la carte (res/drawable), ou un rectangle noir au format carte s'il manque. */
@Composable
internal fun CarteVierge(carte: Carte, modifier: Modifier = Modifier, grand: Boolean = false) {
    val context = LocalContext.current
    val imageId = remember(carte.image) { carte.getDrawableResId(context) }
    val forme = RoundedCornerShape(if (grand) 12.dp else 8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(63f / 88f)
            .clip(forme)
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.08f), forme)
    ) {
        if (imageId != 0) {
            Image(
                painter = painterResource(imageId),
                contentDescription = carte.nom,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            InfosSansVisuel(carte, grand)
        }

        if (carte.quantity > 1) {
            Text(
                "×${carte.quantity}",
                color = RiftBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.White)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

/** Sans visuel, on ecrit sur le fond noir ce que la carte imprimee montrerait. */
@Composable
private fun BoxScope.InfosSansVisuel(carte: Carte, grand: Boolean) {
    // Cout dans le coin, comme sur les vraies cartes.
    Box(
        Modifier
            .padding(8.dp)
            .size(if (grand) 34.dp else 24.dp)
            .border(1.5.dp, couleurDe(carte), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "${carte.cost}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (grand) 16.sp else 12.sp
        )
    }

    Column(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .background(couleurDe(carte))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            carte.nom,
            color = Color.White,
            fontFamily = Title,
            fontWeight = FontWeight.Bold,
            fontSize = if (grand) 18.sp else 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            TYPES.firstOrNull { it.first == carte.type }?.second ?: carte.type,
            color = Muted,
            fontSize = if (grand) 12.sp else 10.sp
        )
    }
}

@Composable
private fun FicheCarte(carte: Carte, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(RiftBlue)
                .border(1.dp, RiftLine)
                .padding(20.dp)
        ) {
            CarteVierge(carte, Modifier.padding(horizontal = 36.dp), grand = true)

            Text(
                carte.nom.uppercase(),
                color = Color.White,
                fontFamily = Title,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                listOf(carte.set, carte.image, carte.rarete).filter { it.isNotEmpty() }
                    .joinToString("  ·  ") { it.uppercase() },
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))
            Caracteristique("Domaine", carte.couleur.replaceFirstChar { it.uppercase() })
            Caracteristique("Type", TYPES.firstOrNull { it.first == carte.type }?.second ?: carte.type)
            Caracteristique("Coût", "${carte.cost}")
            Caracteristique("Power", "${carte.power}")
            if (carte.type == "unite") Caracteristique("Might", "${carte.might}")
            if (carte.motsCles.isNotEmpty()) Caracteristique("Mots-clés", carte.motsCles.replace(",", ", "))
            if (carte.tag.isNotEmpty()) Caracteristique("Tags", carte.tag.replace(",", ", "))

            Spacer(Modifier.height(18.dp))
            Text(
                if (carte.quantity > 0) "Exemplaires possédés : ${carte.quantity}"
                else "Pas encore obtenue. Cherche-la dans la boutique pendant une partie.",
                color = if (carte.quantity > 0) Color.White else RiftOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(BorderStroke(1.dp, RiftLine), RectangleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("Fermer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Caracteristique(label: String, valeur: String) {
    Column {
        Row(Modifier.padding(vertical = 7.dp)) {
            Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valeur, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(RiftLine.copy(alpha = 0.6f))
        )
    }
}
