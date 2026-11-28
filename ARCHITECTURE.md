# PACBOUND : architecture

On marche dans les vraies rues pour ramasser des pièces avant la fin du chrono,
puis on les dépense dans une boutique de cartes Riftbound pour compléter sa collection.

Le code est rangé en **MVC** dans `app/src/main/java/com/maxim/paclearn/` :

| Dossier | Rôle | Règle |
|---|---|---|
| `model/` | Les données et les règles pures | Aucun écran, aucun Android (sauf `Carte.getDrawableResId`) |
| `view/` | Tout ce qui s'affiche : écrans Compose et dessin du plan | Reçoit des données et des callbacks, ne lance rien elle-même |
| `controller/` | Les Activities, la logique de partie et de boutique, le stockage, le réseau | Relie le modèle et la vue |

## model/

| Fichier | À quoi il sert |
|---|---|
| `Carte.kt` | Une carte Riftbound : nom, set, rareté, stats, image. Lecture et écriture JSON. |
| `Amelioration.kt` | Une amélioration de boutique (ex. « 15 % de chance de shield »), valable pour une seule partie. |
| `Partie.kt` | `VALEUR_PIECE` (2 €) et `RecapPartie`, le bilan de fin de partie. |
| `Plan.kt` | Les rues (`Rue`, `Zone`) et `placerPieces` : une pièce tous les 90 m, sur le trottoir, hors des bâtiments. `celluleDe` sert à reconnaître une pièce déjà ramassée. |

## view/

| Fichier | À quoi il sert |
|---|---|
| `Theme.kt` | `PacTheme` : les couleurs de `colors.xml` appliquées à Material. |
| `Composants.kt` | Les briques communes : `Titre`, `Bouton`, `Panneau`, `Fenetre`, `LigneInfo`, `VisuelCarte`, `formatTemps`. |
| `AccueilVue.kt` | L'accueil : choix de la durée (5, 10, 15 ou 30 min) et bouton « Ma collection ». |
| `JeuVue.kt` | Le HUD affiché par-dessus le plan : chrono, argent, boutons Quitter et Boutique, messages, mention OpenStreetMap. |
| `PlanVue.kt` | Les calques dessinés sur la carte osmdroid : rues et bâtiments, pièces, joueur (smiley, zone de ramassage, cône de regard). |
| `BoutiqueVue.kt` | L'écran boutique : 3 cartes, la réserve, 2 améliorations, Relancer. |
| `RecapVue.kt` | La fenêtre « Quitter ? » et l'écran de bilan. |
| `CollectionVue.kt` | L'album : recherche, filtres (domaine, type), tri, fiche détaillée d'une carte. |

## controller/

| Fichier | À quoi il sert |
|---|---|
| `AccueilActivity.kt` | Point d'entrée de l'appli. Ouvre une partie ou la collection. |
| `JeuActivity.kt` | La partie : GPS (avec filtrage des positions absurdes), chrono, ramassage des pièces, chargement de la zone, fin de partie. Les réglages (zoom, rayon, espacement…) sont dans son `companion object`. |
| `CollectionActivity.kt` | Charge le catalogue et la collection, puis les passe à `EcranCollection`. |
| `Boutique.kt` | La logique de la boutique : prix selon la rareté, tirage pondéré (50/30/15/5), réserve, améliorations. |
| `Boussole.kt` | Lit l'orientation du téléphone pour tourner le regard du joueur. |
| `Overpass.kt` | Télécharge les rues et bâtiments autour du joueur depuis OpenStreetMap. |
| `storage/CarteStorage.kt` | La collection du joueur (`files/storage_collection.json`). |
| `storage/Catalogue.kt` | Toutes les cartes et améliorations du jeu, lues dans `assets/data.json`. |
| `storage/Reserve.kt` | Les 2 cartes mises de côté pour une prochaine partie. |
| `storage/RemoteJsonFetcher.kt` | Requêtes JSON vers le serveur (Volley). |
| `storage/JsonFileUtils.kt` | Lire un fichier de `assets/`. |
| `storage/utility/` | Le stockage générique en fichier : `Storage`, `FileStorage`, `JSONFileStorage`. |

## Déroulé d'une partie

```
AccueilActivity ──(durée)──▶ JeuActivity ──▶ Boutique (par-dessus) ──▶ Recap ──▶ retour à l'accueil
       └──────────────▶ CollectionActivity
```

1. `JeuActivity` récupère la position GPS et télécharge un carré de 800 m de rues (`Overpass.kt`).
2. `placerPieces` (`model/Plan.kt`) pose les pièces, et `PlanVue.kt` dessine le tout.
3. Chaque pièce à moins de 15 m rapporte 2 €. Après 200 m parcourus, la zone est rechargée. Les pièces déjà prises ne reviennent pas.
4. L'argent se dépense dans la boutique. Les cartes achetées vont dans la collection, et l'argent qui reste est perdu en fin de partie.

## Tests

`app/src/test/java/com/maxim/paclearn/model/PlacementPiecesTest.kt` vérifie le placement des
pièces : jamais sur la chaussée ni dans un bâtiment, alternance des trottoirs, écart minimum.

```
gradlew testDebugUnitTest
```
