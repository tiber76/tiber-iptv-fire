# StreamVault reprise roadmap

Source d'inspiration: https://github.com/Davidona/StreamVault-IPTV

Note licence: reprendre les idées d'architecture et d'UX uniquement, sauf validation explicite de compatibilité de licence.

## P0 - Stabiliser la synchro longue et le cache chaud

Statut: terminé.

Objectif: accepter une synchro plus longue, mais obtenir ensuite un catalogue fluide, avec affiches et fiches déjà prêtes.

Travail prévu:
- Précharger les affiches des premières lignes visibles et des catégories prioritaires.
- Préhydrater un lot borné de fiches films/séries pendant la synchro.
- Garder un loader clair sur `Recharger` tant que le travail utile est en cours.
- Ne jamais bloquer la lecture ou le clic utilisateur hors phase de synchronisation explicite.

Validation:
- Après `Recharger`, les premières lignes du catalogue doivent afficher les miniatures rapidement.
- Les premières fiches film/série doivent s'ouvrir depuis le cache quand elles ont été hydratées.
- La compilation Kotlin et les tests unitaires passent.

## P1 - Synchro en arrière-plan avec WorkManager

Statut: terminé.

Objectif: préparer le cache sans attendre une action utilisateur.

Travail prévu:
- Programmer un refresh périodique du catalogue.
- Ajouter retry/backoff si serveur IPTV lent ou indisponible.
- Lancer un préchauffage images/fiches après refresh réussi.
- Exposer le dernier statut de synchro dans l'app.

Validation:
- Le worker peut être déclenché sans UI.
- Les erreurs réseau sont journalisées sans casser l'app.
- Le cache reste consultable même si le refresh échoue.

## P2 - Recherche globale SQL/FTS

Statut: terminé.

Objectif: rendre la recherche instantanée sur gros catalogue.

Travail prévu:
- Ajouter des tables FTS Room pour chaînes, films et séries.
- Indexer les champs utiles: titre, catégorie, année, genre si disponible.
- Brancher la recherche globale sur FTS au lieu de filtrer en Kotlin.

Validation:
- Recherche rapide sur live, films et séries.
- Résultats triés de manière stable.
- Fallback propre si les tables FTS doivent être reconstruites.

## P3 - Diagnostic cache et serveur

Statut: terminé.

Objectif: rendre les lenteurs IPTV visibles et actionnables.

Travail prévu:
- Afficher dernière synchro réussie, durée, volumes par type.
- Afficher taille estimée cache DB, cache HTTP et cache images.
- Ajouter un état serveur simple: succès récent, erreur récente, latence approximative.

Validation:
- Les infos aident à distinguer serveur lent, cache vide et problème réseau local.
- Aucun diagnostic ne bloque le thread UI.

## P4 - Cache détail avec TTL par type

Statut: terminé.

Objectif: garder les données stables longtemps, expirer seulement ce qui bouge.

Travail prévu:
- TTL long pour films/séries.
- TTL court pour contenus live/EPG.
- Rafraîchissement silencieux si cache disponible mais ancien.

Validation:
- Une fiche s'ouvre immédiatement depuis cache.
- Le refresh distant met à jour sans clignotement UI.

## P5 - UX catalogue TV

Statut: terminé.

Objectif: accélérer l'usage quotidien sur télécommande.

Travail prévu:
- Catégories épinglées.
- Catégories masquées.
- Groupes personnalisés via une rangée `Mon groupe` alimentée par les catégories choisies.
- Récents et favoris en haut.

Validation:
- Navigation plus courte vers les contenus habituels.
- Préférences persistées localement.

## P6 - Historique enrichi et reprise

Statut: terminé.

Objectif: mieux exploiter les données d'usage local.

Travail prévu:
- Derniers films/séries ouverts.
- Dernières chaînes regardées.
- Section `Continuer` plus visible.
- Tri par reprise disponible.

Validation:
- Les contenus récents remontent sans appel réseau.
- La reprise reste fiable après redémarrage.

## P7 - Politique lecteur plus explicite

Statut: terminé.

Objectif: fiabiliser lecture live/VOD et mieux contrôler les cas lents.

Travail prévu:
- Séparer les politiques timeout, retry, buffer et preload.
- Ajuster les paramètres live vs VOD.
- Ajouter des messages d'échec plus exploitables.

Validation:
- Les erreurs lecture sont plus lisibles.
- Les changements de buffer restent confinés au lecteur.
