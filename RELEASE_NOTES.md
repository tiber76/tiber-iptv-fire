# Release notes

## Tiber IPTV 0.3.10

Version corrective UI pour les reglages TV.

### Corrections

- Suppression du bouton `Choisir dossier USB` dans les reglages.
- Ecran `Reglages` plus compact: marges reduites, cartes plus petites, profils reseau moins hauts.
- Selecteur de profil reseau allege pour donner moins d'effet de zoom sur TV.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.10`.
- Version code: `43`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.10-43-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.10-43-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.9

Version Fire OS 8 / Fire TV Stick 4K Max 2nd gen orientee stockage USB externe.

### Nouveautes et corrections

- Ajout du module `ExternalStorageManager` avec logs `ExternalStorage`.
- Detection Fire TV, SDK Android et constructeur au demarrage du module.
- Enumeration des volumes USB via `StorageManager.storageVolumes`, filtres `isRemovable && !isPrimary`.
- Lancement SAF cible sur le volume USB via `StorageVolume.createOpenDocumentTreeIntent()` quand Fire OS le permet.
- Fallback explicite sur le dossier app-specific USB via `getExternalFilesDirs(null)` quand SAF est absent ou inutilisable.
- Retrait de `MANAGE_EXTERNAL_STORAGE`, non souhaite pour Amazon Appstore.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.9`.
- Version code: `42`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.9-42-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.9-42-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.8

Version de test Fire Stick pour les cles USB en stockage externe/media quand le selecteur de dossier Android est absent.

### Nouveautes et corrections

- Ajout du bouton `Autoriser stockage externe` dans `Reglages > Stockage et caches`.
- Si l'autorisation fichiers est accordee, le mode auto essaie aussi les dossiers publics `TiberIPTV/downloads` sur les volumes externes detectes.
- Le bouton `Choisir dossier USB` reste disponible seulement comme option secondaire pour les appareils qui ont un selecteur compatible.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.8`.
- Version code: `41`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.8-41-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.8-41-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.7

Version corrective Fire Stick pour eviter le redemarrage de l'app si le selecteur de dossier Android est absent.

### Correction

- Le bouton `Choisir dossier USB` ne laisse plus crasher l'application si Fire OS ne fournit pas de selecteur de dossier compatible.
- Un message indique maintenant que le selecteur est indisponible sur l'appareil.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.7`.
- Version code: `40`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.7-40-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.7-40-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.6

Version corrective Fire Stick pour les cles USB disponibles uniquement en stockage externe/media.

### Nouveautes et corrections

- Ajout d'un choix manuel de dossier USB dans `Reglages > Stockage et caches`.
- Ecriture des nouveaux telechargements via l'autorisation Android persistante `content://` quand un dossier USB est choisi.
- Lecture, taille et suppression des telechargements supportees pour les fichiers internes existants et les nouveaux fichiers USB.
- Conservation du mode automatique pour les Fire TV qui exposent le stockage USB comme volume app-specifique.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.6`.
- Version code: `39`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.6-39-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.6-39-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.5

Version corrective Fire Stick pour les cles USB que Fire OS ne remonte pas dans les repertoires externes standards de l'application.

### Corrections

- APK declarant `installLocation="preferExternal"` pour permettre a Fire OS de deplacer/utiliser l'application avec le stockage USB configure en stockage interne.
- Detection stockage etendue aux volumes exposes par `StorageManager`, avec reconstruction du dossier app-specifique `Android/data/com.tiberiptv.fire/files/downloads`.
- Conservation de la detection precedente via `getExternalFilesDirs` et `externalMediaDirs`.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.5`.
- Version code: `38`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.5-38-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.5-38-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.4

Version corrective Fire Stick / Android TV pour mieux detecter les stockages USB externes disponibles pour les telechargements.

### Corrections

- Detection stockage etendue aux repertoires media externes de l'application, en plus des repertoires fichiers externes Android.
- Choix du volume de telechargement conserve sur le stockage accessible avec le plus d'espace disponible.
- Recherche des films deja telecharges conservee sur les anciens chemins internes et externes.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.4`.
- Version code: `37`.
- Regle remote conservee: une seule action Xtream a la fois via `RemoteActionGuard`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.4-37-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.4-37-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.3

Version officielle Fire Stick / Android TV orientee fluidite catalogue et corrections UI telecommande.

### Nouveautes et ameliorations

- Catalogue plus fluide avec plus de 10 000 contenus: chargement du cache local et construction des lignes premium hors thread UI.
- Recherche catalogue optimisee: index memoire pre-calcule, score de recherche calcule une seule fois par item, normalisation sans regex sur le chemin chaud.
- Popin de recherche simplifiee pour Fire TV: clavier ouvert directement, validation via touche lecture/search/entree, retour qui masque le clavier puis ferme la popin.
- Home plus propre: selecteurs Films / Series / Chaines TV Live recolores avec accents de section au lieu du bloc gris de focus.
- Pictogramme `Recharger` du catalogue redessine pour rester lisible dans les boutons compacts.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.3`.
- Version code: `36`.
- Regle remote conservee: une seule action Xtream a la fois via `RemoteActionGuard`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.3-36-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.3-36-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.2

Version officielle Fire Stick / Android TV avec corrections player, catalogue et stockage.

### Nouveautes et ameliorations

- Player TV plus coherent: menu uniforme, bouton retour aligne, disparition automatique du retour avec les controles, bouton `Relancer` retire.
- Clic `Reprendre` depuis la home corrige: lance directement le player au lieu d'ouvrir le catalogue.
- Page `Telecharges` revue: panneau stockage compact, etat vide contraint dans l'ecran, recherche masquee dans `Favoris` et `Telecharges`.
- Recherche catalogue amelioree: bouton `Effacer` direct dans le header et navigation telecommande corrigee dans la popin.
- Prechargement corrige: progression basee sur le seuil reel du profil reseau, plus de faux blocage autour de 65%.
- Catalogue complet conserve localement via Room sans JSON massif dans `CursorWindow`.
- Fiches contenu enrichies quand le serveur fournit les donnees: date de sortie, classification/PEGI, casting, realisation.
- Meilleure preservation du cache catalogue si un rechargement renvoie une liste vide.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.2`.
- Version code: `35`.
- Regle remote conservee: une seule action Xtream a la fois via `RemoteActionGuard`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.2-35-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.2-35-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.1

Version corrective orientee Fire Stick / Android TV.

### Nouveautes et ameliorations

- Recherche catalogue plus fiable: resultats regroupes dans une seule liste, dedoublonnes, avec matching plus tolerant aux accents et a la ponctuation.
- Popin de recherche plus rapide: clavier ouvert directement et validation clavier qui lance la recherche sans cliquer sur le bouton.
- Chargement des affiches plus robuste: les requetes posters mises en pause pendant le scroll ou avant lecture retentent apres la pause.
- Home Fire Stick allegee: moins d'animations de focus, moins d'ombres et moins d'effets de fond pour reduire la latence telecommande.
- Rechargement automatique catalogue limite aux categories jamais chargees ou plus anciennes que 7 jours; avant cela, le rechargement reste manuel.
- Rechargement automatique catalogue decale au demarrage pour laisser la navigation initiale plus fluide.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.1`.
- Version code: `34`.
- Regle remote conservee: une seule action Xtream a la fois via `RemoteActionGuard`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.1-34-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.1-34-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.

## Tiber IPTV 0.3.0

Version officielle orientee Fire Stick / Android TV.

### Nouveautes

- Nouvelle home TV plus premium: compte actif, profils reseau, rechargement par categorie et statuts catalogue.
- Catalogue local persistant: les donnees restent disponibles meme apres 24h, avec recharge conseillee au lieu d'un vidage fragile.
- Compteurs Films / Series / Chaines TV Live persistants localement.
- Header catalogue plus compact, avec navigation a gauche et outils Recherche / Filtres / Tri a droite.
- Miniatures catalogue plus denses pour afficher davantage de contenus sur TV 4K.
- Recherche catalogue corrigee pour la telecommande: le clavier ne s'ouvre plus automatiquement et les boutons restent accessibles.
- Chargement d'affiches mis en pause pendant le scroll et avant lecture pour prioriser la navigation et le playback.
- Prechargement progressif plus clair pour les connexions lentes ou instables.
- Player TV ameliore: controles plus discrets, feedback avance/retour, format image, audio et sous-titres.
- Section `Telecharges` avec poids local, suppression et meilleure coherence des boutons sur fiche locale.
- Tests unitaires ajoutes sur la persistance catalogue.

### Corrections

- Correction des compteurs a `0` apres recharge locale/cache fragile.
- Correction du nettoyage du cache catalogue Room lors du changement de compte.
- Correction du focus telecommande dans la popin de recherche.
- Correction de plusieurs libelles et etats UI: `Telecharges`, `Chaines TV Live`, `Sous-titres`, `Relancer`.
- Correction des labels coupes dans la home et le header catalogue.

### Notes techniques

- Package Android conserve: `com.tiberiptv.fire`.
- Version: `0.3.0`.
- Version code: `33`.
- Regle remote conservee: une seule action Xtream a la fois via `RemoteActionGuard`.
- L'APK release est signe avec le keystore local du projet.

### Installation

Installer l'APK `tiber-iptv-0.3.0-33-release.apk` depuis la release GitHub.

Sur Fire Stick deja connecte en ADB:

```sh
adb install -r tiber-iptv-0.3.0-33-release.apk
```

L'installation avec `-r` remplace l'ancienne version sans supprimer les donnees locales de l'application.
