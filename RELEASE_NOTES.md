# Release notes

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
