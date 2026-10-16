package com.maxim.paclearn

import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.storage.CarteStorage
import com.maxim.paclearn.storage.readJsonFromAssets
import org.json.JSONObject

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
    val cartes = remember { mutableStateListOf<Carte>() }
    var isLoading by remember { mutableStateOf(true) }

    var selectedColor by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var cardDetail by remember { mutableStateOf<Carte?>(null) }

    fun reloadCollection() {
        cartes.clear()
        cartes.addAll(storage.getCollection())
    }

    LaunchedEffect(Unit) {
        if (storage.size() == 0) {
            // Initialisation avec le catalogue local pour le premier lancement
            try {
                val jsonString = readJsonFromAssets(context, "data.json")
                val root = JSONObject(jsonString)
                val cartesObj = root.optJSONObject("cartes")
                if (cartesObj != null) {
                    val keys = cartesObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val cardJson = cartesObj.getJSONObject(key)
                        val c = Carte.fromJson(cardJson)
                        storage.insert(c.uniqueId(), c)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        reloadCollection()
        isLoading = false
    }

    val filteredCartes = cartes.filter { carte ->
        val matchColor = selectedColor == null || carte.couleur.equals(selectedColor, ignoreCase = true)
        val matchType = selectedType == null || carte.type.equals(selectedType, ignoreCase = true)
        val matchQuery = searchQuery.isBlank() || carte.nom.contains(searchQuery, ignoreCase = true) || carte.tag.contains(searchQuery, ignoreCase = true)
        matchColor && matchType && matchQuery
    }

    val totalCopies = cartes.sumOf { it.quantity }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightHigh, Night)))
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // En-tête
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MA COLLECTION",
                        color = Smiley,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${cartes.size} cartes uniques • $totalCopies exemplaires",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
            }

            // Barre de recherche
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher une carte ou un tag...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedBorderColor = Sky,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            // Filtres par couleur / domaine
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "Toutes",
                    isSelected = selectedColor == null,
                    badgeColor = Sky,
                    onClick = { selectedColor = null }
                )
                FilterChipItem("Vert (Calm)", selectedColor == "vert", Color(0xFF43A047)) { selectedColor = if (selectedColor == "vert") null else "vert" }
                FilterChipItem("Rouge (Fury)", selectedColor == "rouge", Color(0xFFE53935)) { selectedColor = if (selectedColor == "rouge") null else "rouge" }
                FilterChipItem("Bleu (Mind)", selectedColor == "bleu", Color(0xFF1E88E5)) { selectedColor = if (selectedColor == "bleu") null else "bleu" }
                FilterChipItem("Orange (Body)", selectedColor == "orange", Color(0xFFFB8C00)) { selectedColor = if (selectedColor == "orange") null else "orange" }
                FilterChipItem("Pourpre (Chaos)", selectedColor == "pourpre", Color(0xFF8E24AA)) { selectedColor = if (selectedColor == "pourpre") null else "pourpre" }
                FilterChipItem("Jaune (Order)", selectedColor == "jaune", Color(0xFFFDD835)) { selectedColor = if (selectedColor == "jaune") null else "jaune" }
            }

            // Filtres par type de carte
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeChipItem("Tous les types", selectedType == null) { selectedType = null }
                TypeChipItem("Unités", selectedType == "unite") { selectedType = if (selectedType == "unite") null else "unite" }
                TypeChipItem("Sorts", selectedType == "sort") { selectedType = if (selectedType == "sort") null else "sort" }
                TypeChipItem("Équipements", selectedType == "equipement") { selectedType = if (selectedType == "equipement") null else "equipement" }
            }

            Spacer(Modifier.height(8.dp))

            // Grille de cartes
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Smiley)
                }
            } else if (filteredCartes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Aucune carte trouvée", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Modifiez vos filtres ou effectuez une recherche différente.",
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredCartes, key = { it.uniqueId() }) { carte ->
                        CardGridItem(carte = carte, onClick = { cardDetail = carte })
                    }
                }
            }
        }

        // Modal détaillé au clic sur une carte
        cardDetail?.let { selected ->
            CardDetailDialog(
                carte = selected,
                onDismiss = { cardDetail = null },
                onAddCopy = {
                    storage.addOrIncrement(selected, 1)
                    reloadCollection()
                    cardDetail = storage.find(selected.uniqueId())
                },
                onRemoveCopy = {
                    storage.removeOrDecrement(selected, 1)
                    reloadCollection()
                    cardDetail = storage.find(selected.uniqueId())
                    if (cardDetail == null) cardDetail = null
                }
            )
        }
    }
}

@Composable
fun CardGridItem(carte: Carte, onClick: () -> Unit) {
    val context = LocalContext.current
    val imageResId = remember(carte.image) { carte.getDrawableResId(context) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = BorderStroke(
            1.dp,
            if (carte.foil) Smiley.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                if (imageResId != 0) {
                    Image(
                        painter = painterResource(id = imageResId),
                        contentDescription = carte.nom,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = carte.nom, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }

                // Badge Quantité
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "x${carte.quantity}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Badge Foil si applicable
                if (carte.foil) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Smiley,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "✨ FOIL",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Informations sous l'image
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = carte.nom,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = carte.type.replaceFirstChar { it.uppercase() },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = carte.rarete.uppercase(),
                        color = when (carte.rarete.lowercase()) {
                            "epique" -> Color(0xFFFFB300)
                            "rare" -> Sky
                            else -> Color.White.copy(alpha = 0.5f)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(label: String, isSelected: Boolean, badgeColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) badgeColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (isSelected) badgeColor else Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun TypeChipItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Sky.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, if (isSelected) Sky else Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun CardDetailDialog(
    carte: Carte,
    onDismiss: () -> Unit,
    onAddCopy: () -> Unit,
    onRemoveCopy: () -> Unit
) {
    val context = LocalContext.current
    val imageResId = remember(carte.image) { carte.getDrawableResId(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = NightHigh,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Grande image
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    if (imageResId != 0) {
                        Image(
                            painter = painterResource(id = imageResId),
                            contentDescription = carte.nom,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = carte.nom,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${carte.set.uppercase()} • ${carte.image} • ${carte.rarete.uppercase()}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Stats de jeu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill("Coût", "${carte.cost}")
                    StatPill("Power", "${carte.power}")
                    if (carte.type == "unite") {
                        StatPill("Might", "${carte.might}")
                    }
                    StatPill("Type", carte.type.uppercase())
                }

                if (carte.motsCles.isNotEmpty() || carte.tag.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Mots-clés : ${carte.motsCles.ifEmpty { "—" }}  |  Tags : ${carte.tag.ifEmpty { "—" }}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Contrôle de quantité (test de persistance locale)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exemplaires : x${carte.quantity}",
                        color = Smiley,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRemoveCopy,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.size(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("-", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onAddCopy,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Sky),
                            modifier = Modifier.size(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f))
                ) {
                    Text("Fermer", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
