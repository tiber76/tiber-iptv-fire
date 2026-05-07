# Tiber IPTV Fire

MVP Android TV / Fire TV pour lire une source personnelle Xtream Codes.
L'application ne fournit aucun flux, aucune playlist et aucun acces IPTV.

## Scope actuel

- Connexion Xtream Codes: serveur, identifiant, mot de passe.
- Authentification via `player_api.php`.
- Sections Live, Films et Series.
- Section Local pour relire les films et episodes telecharges.
- Recuperation des categories Xtream.
- Carrousels TV focusables a la telecommande.
- Posters distants avec cache memoire et disque.
- Lecture Live, VOD et episodes via LibVLC pour mieux couvrir AC3, E-AC3, DTS et autres pistes audio non supportees par Android.
- Player avec boutons Quitter, Lecture/Pause, Audio, Sous-titres, Diagnostic et Relancer.
- Selection manuelle des pistes audio et sous-titres detectes.
- Diagnostic codec, resolution, pistes audio et sous-titres dans le player.
- Navigation Fire TV basique: D-pad, OK, Back, Menu pour reglages.
- Suppression locale du compte.
- Souris/trackpad sur macOS: clic boutons/cartes/champs et molette de scroll.
- Recherche locale dans l'ecran courant.
- Favoris, historique, reprise et cache des sections persistants via Room.
- EPG court Xtream sur les chaines live.
- Details VOD/series: synopsis, genre, duree, note, date, casting et realisation quand le fournisseur les renvoie.
- Telechargement local des films et episodes via le gestionnaire de telechargement Android.
- Verification de l'espace disponible avant telechargement quand le serveur renvoie la taille du fichier.
- Progression du telechargement dans le header pendant que l'utilisateur continue a naviguer.
- Fallback automatique Live `TS` <-> `M3U8` si le premier format echoue.
- Skeleton loading sur les carrousels.
- Choix du format live `ts` ou `m3u8`.

## Controles

- Fleches clavier / telecommande: navigation.
- Entree / OK: action.
- Back / Esc: retour.
- Menu: reglages.
- Dans le player, `Menu`: infos lecture.
- Clic souris: selectionne les champs, boutons et cartes.
- Clic long sur une carte: ajoute ou retire un favori.
- Molette verticale: scroll de page.
- Molette sur une rangee: scroll horizontal.

Sur certains emulateurs Android TV, la souris hote est exposee comme un pointeur TV et non comme un tactile classique. L'app intercepte donc les evenements pointeur globalement, mais le test le plus fiable reste les fleches/Entree ou un Fire Stick reel.

## Diagnostic audio et codecs

Le player utilise LibVLC afin de decoder en logiciel les pistes que le framework Android refuse souvent sur tablette/emulateur, par exemple AC3 5.1, E-AC3 et DTS. Si un flux reste muet:

1. Ouvrir `Diagnostic` dans le player.
2. Verifier les pistes audio detectees.
3. Essayer le bouton `Audio` pour changer de piste.
4. Essayer le format live `M3U8` dans `Reglages` si le flux direct est en TS.

Pour le live, l'app tente automatiquement le second format (`TS` ou `M3U8`) si le premier flux renvoie une erreur de lecture.

## Build

```sh
./gradlew :app:assembleDebug
```

APK genere:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Lancer en une commande sur macOS

Avec un emulateur Android TV cree dans Android Studio:

```sh
./scripts/run-tv.sh
```

Le script construit l'APK, demarre un emulateur Android TV si aucun appareil ADB n'est deja disponible, installe l'app, puis la lance.

Pour forcer un emulateur precis:

```sh
AVD_NAME="Android_TV_1080p_API_35" ./scripts/run-tv.sh
```

Pour utiliser un Fire Stick deja connecte en ADB:

```sh
SKIP_EMULATOR=1 DEVICE_SERIAL=<ip-du-fire-stick>:5555 ./scripts/run-tv.sh
```

## Installation sur Fire Stick

1. Activer les options developpeur sur Fire TV.
2. Activer le debogage ADB.
3. Recuperer l'adresse IP du Fire Stick.
4. Depuis ce dossier:

```sh
adb connect <ip-du-fire-stick>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build release signe

Pour produire un APK release localement signe:

```sh
./scripts/build-firestick-release.sh
```

Le script genere un keystore local ignore par Git dans `release/`, puis produit:

```text
app/build/outputs/apk/release/app-release.apk
```

## Telechargements

Les films et episodes peuvent etre telecharges en arriere-plan depuis leur fiche detail. L'app verifie l'espace libre avant de lancer le telechargement lorsque le serveur expose la taille du fichier. La progression apparait dans le header, Android affiche une notification a la fin, puis le contenu est accessible depuis l'onglet `Local`.

## Architecture

- `MainActivity`: connexion, navigation TV, categories, carrousels.
- `PlayerActivity`: lecteur plein ecran LibVLC.
- `XtreamApi`: appels Xtream Codes et generation des URLs de lecture.
- `CredentialStore`: stockage local des identifiants via `SharedPreferences`.
- `AppStateStore` / `TiberDatabase`: cache local, historique, favoris, reprise et format live.
- `PosterLoader`: chargement asynchrone des images.

## Prochaines etapes recommandees

- Ajouter une UI Compose for TV plus proche d'une app premium.
- Ajouter tests unitaires du parsing Xtream avec fixtures JSON.
- Tester sur Fire Stick 4K reel avec des flux AC3/E-AC3/DTS/HEVC 4K.
- Ajouter une vraie grille XMLTV multi-chaines et pas seulement l'EPG court Xtream.
