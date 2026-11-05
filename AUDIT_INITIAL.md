# Audit initial — Taqnin ID Batch Android

Audit en lecture seule réalisé le 5 novembre 2026, avant toute correction, sur la branche par défaut `claude/taqnin-id-batch-app-fzP1g` (commit `4e166f9`).

## 1. État du dépôt

| Élément | Constat |
|---|---|
| Branche par défaut | `claude/taqnin-id-batch-app-fzP1g`, une seule branche, aucune branche `main` |
| Derniers commits | « ajouter script de seed des comptes utilisateurs de test » |
| Pull requests | Aucune, ni ouverte ni fusionnée |
| GitHub Actions | Aucun workflow |
| Tests | **Aucun** (ni `src/test`, ni `src/androidTest`) |
| Wrapper Gradle | **Absent** (`gradlew` et `gradle/wrapper` manquants) |
| APK publié | Aucun |
| Règles Firestore | **Absentes du dépôt** (la sécurité dépendait de la console, invérifiable) |
| `google-services.json` | Fichier factice (clé `REMPLACEZ_PAR_VOTRE_CLE_API`), aucun secret |
| Pile | Kotlin 1.9.22, AGP 8.2.2, compileSdk/targetSdk 34, minSdk 26, MVVM, View Binding, Navigation, Room 2.6.1, WorkManager 2.9, Firebase Auth et Firestore (BoM 32.7.2), CameraX 1.3.1, ML Kit 17.2, ZXing, iText 5 |

## 2. Le projet ne compilait pas

Une première compilation en CI, sur le code d'origine, a montré que **le projet n'avait jamais pu compiler**. Les causes, découvertes l'une après l'autre :

1. `build.gradle` déclarait des dépôts `allprojects` alors que `settings.gradle` les interdit (`FAIL_ON_PROJECT_REPOS`).
2. `gradle.properties` manquait, donc AndroidX n'était pas activé et le plugin safe-args refusait de configurer le projet.
3. `item_selected_batch.xml` utilisait le préfixe `app:` sans le déclarer.
4. `themes.xml` héritait de styles inexistants (`ShapeAppearance.Material3.Medium/Large`) et d'un parent implicite absent.
5. `activity_login.xml` référençait `?attr/colorBackground` au lieu de `?android:attr/colorBackground`.
6. `fragment_batch_detail.xml` incluait une grille sans identifiant : les 13 champs que le code lisait n'étaient pas accessibles par View Binding.
7. Le fichier `google-services.json` ne déclarait pas le paquet `.debug`, ce qui faisait échouer la compilation debug.

## 3. Fonctionnalités

### Présentes

- Connexion e-mail et mot de passe, déverrouillage biométrique.
- Lots : création, liste, recherche, détail, historique des actions, QR code.
- Scanner CameraX et ML Kit.
- Transferts : manifeste, QR de transport, export PDF.
- Rapports PDF et CSV.
- Base locale Room et synchronisation WorkManager vers Firestore.

### Incomplètes ou défectueuses

- **Envoi seulement, aucune récupération.** Une réinstallation ou un autre appareil ne retrouvait rien.
- **Pesée non renvoyée.** Une pesée ou un changement de stade après une première synchronisation n'était jamais renvoyé : `updateQuantity` et `updateStage` ne remettaient pas `isSynced` à 0.
- **Ajout de lot à un transfert.** Il ne se faisait que via « le lot scanné en mémoire » (un `TODO` dans le code).
- **Réception.** Elle reprenait les quantités déclarées sans saisie, et enregistrait le *nom* comme `receivedBy` au lieu de l'UID.
- **Scanner.** Il ne trouvait un transfert que s'il n'était **pas encore** synchronisé.
- **Quantités réceptionnées.** `uploadTransfer` n'envoyait pas `batchSummaries`.
- **Unité.** C'était un texte libre, sans contrôle.
- **Stade.** N'importe quel stade pouvait suivre n'importe quel autre.
- **Quantités.** Elles pouvaient devenir négatives.
- **Biométrie.** En cas d'échec, l'écran de connexion n'avait pas d'écouteurs : l'utilisateur restait bloqué.

## 4. Anomalies critiques de sécurité

| # | Anomalie | Gravité |
|---|---|---|
| S1 | `UserRepository.register(role = …)` écrivait le rôle choisi par le client dans `/users/{uid}` : **auto-attribution du rôle ADMIN** possible. | Critique |
| S2 | Aucune règle Firestore versionnée. La structure (`/batches/{taqninId}` écrit en `set(merge)`) permettait, sans règles strictes, d'écraser le lot d'une autre organisation. | Critique |
| S3 | `role`, `licenseId` et `isActive` étaient envoyés par le client (`uploadUser`) à chaque inscription. | Critique |
| S4 | `canCreate()`, `canTransfer()`, `canScan()` et `canExport()` étaient définies mais **jamais utilisées** : un LECTEUR pouvait créer des lots et des transferts. | Élevée |
| S5 | La base Room n'était pas effacée à la déconnexion. Un autre compte sur le même appareil voyait les lots d'une autre organisation. | Élevée |
| S6 | `allowBackup="true"` : la base locale et les préférences partaient dans les sauvegardes Android. | Moyenne |
| S7 | `getUser` considérait un profil sans `isActive` comme **actif**. | Moyenne |
| S8 | Mots de passe des comptes de test en clair dans `scripts/seed-test-users.js` (`test1234!`). Ces comptes sont créés dans le projet configuré : à ne jamais exécuter sur un projet réel. | Moyenne |

## 5. Intégrité des données et mode hors ligne

| # | Anomalie | Gravité |
|---|---|---|
| D1 | `fallbackToDestructiveMigration()` : toute montée de version de la base **effaçait les saisies non envoyées**. | Critique |
| D2 | `insert(REPLACE)` sur les lots : SQLite supprime puis réinsère la ligne, ce qui **efface en cascade le journal d'actions** (clé étrangère `ON DELETE CASCADE`). | Critique |
| D3 | `markSynced` sans condition : une saisie faite pendant l'envoi était marquée envoyée sans l'avoir été. **Perte silencieuse.** | Critique |
| D4 | Aucune gestion de conflit : la dernière écriture gagnait (`set(merge)`), deux appareils s'écrasaient. | Élevée |
| D5 | Lot et action écrits en deux opérations séparées, sans transaction. | Élevée |
| D6 | Après 3 échecs réseau, le travail passait en échec définitif. | Moyenne |
| D7 | `TAQ-XXXX-AAAA` : seulement 20 bits d'aléa, une collision devient probable vers 1 300 lots par an. Format incompatible avec l'identifiant officiel web (`MA-…`). | Élevée |
| D8 | Numéro de transfert `TRF-AAAA-(ms % 100000)` : collisions entre appareils. | Moyenne |
| D9 | Un transfert ne sortait pas le stock de l'expéditeur. Une double soumission créait deux manifestes. | Élevée |

## 6. Dette technique

- Pas de CI, pas de tests, pas de wrapper : aucune garantie de compilation.
- Textes français écrits en dur dans le code Kotlin (préparation de l'arabe incomplète, même si `supportsRtl="true"`).
- iText 5 (`itextg`) est ancien et sous licence AGPL : à revoir avant toute distribution.
- `Location` est modélisé, mais aucun écran ne le gère.
- Le modèle n'est pas aligné sur V2.5 (voir `ARCHITECTURE_INTEGRATION.md`).

## 7. Plan de correction priorisé

| Priorité | Correction | État |
|---|---|---|
| P0 | Règles Firestore à privilèges minimaux, avec tests émulateur (4 rôles, 2 organisations) | Fait |
| P0 | Fin de l'auto-attribution de rôle ; profil en attente, activation par l'administrateur | Fait |
| P0 | Compilation réparée ; CI avec compilation, tests, lint et APK | Fait |
| P0 | Fin des pertes silencieuses (D1, D2, D3) | Fait |
| P1 | Conflits explicites (version de lot) ; refus conservés avec leur motif | Fait |
| P1 | Rôles appliqués dans l'interface ; données effacées à la déconnexion ; sauvegarde désactivée | Fait |
| P1 | Identifiants à 50 bits ; lecture des identifiants web ; scanner multi-format | Fait |
| P1 | Transferts : sortie de stock atomique, double soumission, réception par le destinataire | Fait |
| P2 | API mobile V2.5 et retrait de Firestore (`ARCHITECTURE_INTEGRATION.md`) | À faire |
| P2 | Externaliser les textes (`strings.xml`) et traduire en arabe | À faire |
| P2 | Remplacer iText 5 ; écran des emplacements | À faire |
