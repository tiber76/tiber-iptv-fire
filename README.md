# Tiber IPTV Fire

Application Android TV / Fire TV en Kotlin + Jetpack Compose pour lire une source personnelle Xtream Codes.

L'application ne fournit aucun flux, aucune playlist et aucun acces IPTV.

## Version actuelle

- Version officielle: `0.3.10`
- Version code: `43`
- Plateformes ciblees: Fire TV / Android TV, avec compatibilite Android classique a valider separement.

## Fonctionnalites principales

- Connexion Xtream Codes par serveur, identifiant et mot de passe.
- Gestion multi-comptes avec changement rapide depuis la home.
- Sections `Direct`, `Films`, `Series`, `Favoris` et `Telecharges`.
- Catalogue local persistant via Room: le catalogue reste disponible meme s'il a plus de 7 jours.
- Rechargement manuel par categorie et rechargement automatique seulement si une categorie n'a jamais ete chargee ou date de plus de 7 jours.
- Regle critique: une seule action remote Xtream a la fois via `RemoteActionGuard`.
- Recherche locale, filtres 4K/note/annee, tris ajout recent/note/A-Z.
- Favoris par clic long sur miniature.
- Historique et reprise de lecture separes films/series.
- Affichage des notes et tags 4K sur les miniatures quand les donnees sont disponibles.
- Posters distants avec cache memoire/disque, pause du chargement pendant le scroll et avant lecture.
- Lecture Live, VOD et episodes via LibVLC pour mieux couvrir AC3, E-AC3, DTS, HEVC et pistes multi-audio.
- Choix audio, sous-titres et format image depuis le player.
- Avance/retour telecommande avec feedback visuel quand la duree est connue.
- Telechargement local des films et episodes via le gestionnaire de telechargement Android.
- Section `Telecharges` avec poids du fichier et suppression.
- Prechargement progressif pour connexions lentes, avec modes adaptes au profil reseau.
- Profils reseau: `Normal`, `VPN / Connexion instable`, `Connexion lente`.
- Ecran stockage: telechargements, cache affiches, cache prechargement temporaire.

## Controles TV

- Fleches telecommande: navigation.
- OK / Entree: action.
- Retour: revient a l'ecran precedent.
- Menu: reglages.
- Clic long sur miniature: ajoute ou retire des favoris.
- Dans le player:
  - OK: lecture / pause selon le focus.
  - Fleche droite/gauche: avance/retour quand disponible.
  - Retour: masque les controles si visibles, quitte seulement si le player est deja en plein ecran.

Sur certains emulateurs Android TV, la souris hote est exposee comme un pointeur TV et non comme un tactile classique. Le test le plus fiable reste les fleches/OK ou un Fire Stick reel.

## Installation sur Fire Stick

Depuis l'APK release GitHub:

1. Installer l'application `Downloader` sur Fire TV.
2. Ouvrir le lien de la release GitHub.
3. Telecharger l'APK `tiber-iptv-0.3.10-43-release.apk`.
4. Autoriser l'installation depuis `Downloader` si Fire OS le demande.
5. Installer l'APK.

Depuis ADB:

```sh
adb connect <ip-du-fire-stick>:5555
adb install -r app/build/outputs/apk/release/tiber-iptv-0.3.10-43-release.apk
```

`adb install -r` remplace l'ancienne version sans supprimer le stockage local tant que le package reste `com.tiberiptv.fire`.

## Build debug local

```sh
./gradlew :app:assembleDebug
```

APK debug:

```text
app/build/outputs/apk/debug/tiber-iptv-0.3.10-43-debug.apk
```

## Build release signe

```sh
./scripts/build-firestick-release.sh
```

Le script genere ou reutilise un keystore local ignore par Git dans `release/`, puis produit:

```text
app/build/outputs/apk/release/tiber-iptv-0.3.4-37-release.apk
```

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

Les tests couvrent notamment la persistance du catalogue local et des compteurs de catalogue.

## Architecture

- `HomeActivity`: home TV, comptes, profils reseau et rechargement rapide du catalogue.
- `MainActivity`: catalogue, fiches contenus, favoris, telechargements, reglages.
- `PlayerActivity`: lecteur plein ecran LibVLC.
- `XtreamApi`: appels Xtream Codes et generation des URLs de lecture.
- `CredentialStore`: stockage local des comptes.
- `AppStateStore` / `TiberDatabase`: catalogue local, historique, favoris, reprise, telechargements et preferences.
- `PosterLoader`: chargement et cache des affiches avec respect du verrou remote.
- `PreloadStreamServer`: prechargement progressif temporaire et nettoyage du cache tampon.

## Notes importantes

- Le catalogue local peut etre ancien mais reste disponible hors recharge.
- Une categorie affiche `Ancien +7j` quand une recharge est conseillee.
- Les affiches peuvent etre de qualite variable selon les URLs fournies par le serveur IPTV.
- Le mode prechargement utilise le stockage temporaire et doit etre nettoye automatiquement.
- L'application ne contourne aucune restriction fournisseur et ne multiplie pas les connexions Xtream en parallele.

## Prochaines etapes recommandees

- Tester `0.3.4` sur Fire Stick 4K avec plusieurs profils reseau.
- Verifier les popins audio/sous-titres/format sur TV 4K.
- Continuer le decoupage de `MainActivity` en composants Compose dedies.
- Ajouter davantage de tests sur le parsing Xtream et le prechargement.
