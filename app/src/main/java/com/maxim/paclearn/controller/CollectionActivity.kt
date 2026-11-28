package com.maxim.paclearn.controller

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxim.paclearn.controller.storage.CarteStorage
import com.maxim.paclearn.controller.storage.Catalogue
import com.maxim.paclearn.model.Carte
import com.maxim.paclearn.view.EcranCollection
import com.maxim.paclearn.view.PacTheme

/** L'album : charge le catalogue et la collection, puis les affiche. */
class CollectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        val cartes = chargerAlbum()
        setContent {
            PacTheme { EcranCollection(cartes, onRetour = ::finish) }
        }
    }

    /** Tout le catalogue, avec le nombre d'exemplaires possedes (0 = pas encore obtenue). */
    private fun chargerAlbum(): List<Carte> {
        val possedees = CarteStorage(this).getCollection().associateBy { it.uniqueId() }
        val catalogue = Catalogue.load(this).cartes
            .map { it.copy(quantity = possedees[it.uniqueId()]?.quantity ?: 0) }
        // Une carte possedee peut ne plus etre au catalogue : on la garde quand meme.
        val horsCatalogue = possedees.values.filter { c -> catalogue.none { it.uniqueId() == c.uniqueId() } }
        return catalogue + horsCatalogue
    }
}
