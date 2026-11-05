# Rapport de tests — Taqnin ID Batch Android V1

5 novembre 2026, branche `claude/android-v1-securite`. Chaque résultat a été obtenu en **exécutant** le test. Ce qui n'a pas été exécuté est indiqué au § 3.

Exécution CI de référence : workflow `Android`, run `42078700972`, 3 jobs verts.

## 1. Résultats

| Contrôle | Où | Résultat |
|---|---|---|
| Compilation debug (Gradle 8.7, AGP 8.2.2, JDK 17) | CI | ✅ (le code d'origine ne compilait pas : 7 défauts corrigés, voir `AUDIT_INITIAL.md`) |
| Tests unitaires JVM et Robolectric (`testDebugUnitTest`) | CI | ✅ **44 / 44** |
| — dont domaine : identifiants, QR, règles de lot, décisions de synchronisation | CI + local (Gradle JVM) | 26 / 26 |
| — dont dépôts Room : transactions, rôles, quantités, stades, synchronisation, conflits, transferts, effacement | CI (Robolectric, SDK 34) | 14 / 14 |
| — dont migration Room 1 → 2 sur une vraie base version 1 | CI (Robolectric) | 1 / 1 |
| — dont exports CSV (colonnes, injection de formules) et manifeste PDF | CI (Robolectric) | 3 / 3 |
| Analyse statique (`lintDebug`, échoue sur erreur) | CI | ✅ |
| Règles Firestore sur l'émulateur (4 rôles, 2 organisations, compte inactif, sans organisation, anonyme) | CI + local | ✅ **63 / 63** |
| Outil d'administration `assign-user.js` (émulateurs Auth et Firestore) | local | ✅ rattachement, trace d'audit, désactivation, rôle inconnu refusé |
| APK de bêta `taqnin-id-batch-android-v1-beta` | CI | ✅ artefact produit (22,5 Mo) |
| Installation et ouverture sur émulateur **Android 15 (API 35)** | CI | ✅ installée ; ouverture à froid (2,9 s) ; écran de connexion affiché ; réouverture après arrêt forcé ; aucune exception fatale ; captures publiées (artefact `emulateur-android-15`) |

## 2. Scénarios demandés par la mission

| Scénario | Couverture | État |
|---|---|---|
| Coupure réseau pendant un transfert | Le transfert est écrit en local dans une transaction ; l'envoi est rejoué jusqu'au succès ; le marquage est conditionnel | Logique testée (dépôts, décisions). Pas de test bout en bout appareil + Firestore |
| Reconnexion après plusieurs heures | WorkManager relance sans limite sur erreur réseau ; récupération incrémentale avec une marge d'un jour | Logique en place, non exécutée en conditions réelles |
| Modification concurrente du même lot | Version de lot ; la règle refuse une version périmée ; décision « conflit » ; choix explicite | ✅ règle (émulateur) + décision (unitaire) + reprise (Robolectric) |
| Double soumission | Transfert : le second appui trouve des lots vides et est refusé ; rejeu d'une action identique accepté sans doublon | ✅ Robolectric + règles |
| Tentative de modification d'un rôle | Refusée par les règles pour les 4 rôles ; un ADMIN ne nomme pas d'ADMIN | ✅ émulateur |
| Accès à une autre organisation | Lecture, écriture, création au nom d'une autre organisation, action sur un lot étranger : refusées | ✅ émulateur |
| Réinstallation et récupération des données autorisées | Récupération des lots, actions et transferts de l'organisation à la synchronisation | Code en place ; non exécuté faute de projet Firebase de recette |
| Scanner QR | Analyse des 4 formats et des codes invalides | ✅ unitaire. Caméra réelle non testée |
| Exports | CSV des lots, actions et transferts ; manifeste PDF | ✅ Robolectric |

## 3. Non exécuté — à faire

| Test | Raison | Comment le faire |
|---|---|---|
| **Samsung Galaxy A14 réel** | Aucun appareil physique accessible | Installer l'APK de l'artefact, dérouler la recette de `GO_NO_GO.md` |
| Connexion, création de lot, synchronisation, déconnexion sur un vrai projet Firebase | Pas de projet de recette : sa création est une ressource externe soumise à autorisation | Secret `GOOGLE_SERVICES_JSON_RECETTE`, puis recette manuelle |
| Tests instrumentés (Espresso) et scan par la caméra réelle | Émulateur sans caméra utile ; temps de CI | Après la mise en place du projet de recette |
| Compilation release (minification R8) | Hors bêta ; pas de clé de signature | Avant toute distribution |
