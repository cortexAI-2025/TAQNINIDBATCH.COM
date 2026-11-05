# GO / NO-GO — Taqnin ID Batch Android V1

Décision technique au 5 novembre 2026, branche `claude/android-v1-securite`.

## Bêta fermée : **GO conditionnel**

| Critère | État | Preuve / réserve |
|---|---|---|
| CI verte | ✅ | Workflow `Android` : règles Firestore, compilation, tests unitaires, lint, APK (voir `TEST_REPORT.md`) |
| Tests critiques réussis | ✅ | 63 tests de règles sur l'émulateur ; tests de domaine, de dépôts Room et de migration |
| Aucune faille critique d'autorisation connue | ✅ | Élévation de privilèges et accès entre organisations refusés par les règles, et testés |
| Isolation des organisations vérifiée | ✅ | Sur l'émulateur Firestore (règles). Côté appareil : base effacée au changement de compte |
| Intégrité des lots et mouvements | ✅ | Journal en ajout seul ; écritures atomiques ; versions anti-écrasement ; migration sans perte |
| Synchronisation hors ligne fiable | ⚠️ | La logique est testée (décisions, marquage conditionnel, rejeu). **Pas encore de test bout en bout appareil + Firestore** : il faut un projet de recette |
| APK installable, parcours principal testé | ⚠️ | Installation et ouverture vérifiées sur émulateur Android 15 (API 35). Connexion, création de lot, scan, hors ligne, synchronisation et déconnexion **restent à tester** avec un projet Firebase de recette |
| Données fictives, environnement isolé | ✅ | Aucun projet réel utilisé ; `google-services.json` factice |

**Conditions pour ouvrir la bêta fermée** :

1. Créer un projet Firebase **de recette** (gratuit, plan Spark). C'est une ressource externe : elle demande votre autorisation.
2. Déployer les règles testées sur **ce** projet uniquement.
3. Ajouter son `google-services.json` dans le secret GitHub `GOOGLE_SERVICES_JSON_RECETTE`.
4. Rattacher les comptes de test avec `scripts/assign-user.js`.
5. Dérouler la recette ci-dessous sur un Samsung Galaxy A14 réel.

Recette manuelle à faire sur le Galaxy A14 (**non faite**, faute d'appareil) :

- installation ;
- connexion ;
- création d'un lot fictif ;
- pesée hors ligne puis synchronisation ;
- scan d'une étiquette ;
- transfert, puis réception sur un second compte ;
- conflit sur deux appareils ;
- déconnexion avec des saisies en attente.

## Production : **NO-GO**

| Motif | Critère de la mission |
|---|---|
| Deux sources de vérité : Android écrit dans Firestore, la référence réglementaire est V2.5 (MySQL) | Interdit par la phase D. API mobile à livrer (`ARCHITECTURE_INTEGRATION.md`) |
| Parcours principal non testé sur un appareil réel avec un serveur réel | Tests critiques incomplets |
| Pas d'App Check ; mot de passe de 6 caractères minimum | Durcissement nécessaire |
| Conformité réglementaire non validée (stades, mouvements, rôles OUVRIER et LECTEUR absents de V2.5) | « Conformité réglementaire non validée » |

## Plan de déploiement futur (non exécuté)

1. Recette Firebase isolée, avec les règles de `firebase/firestore.rules` (`firebase deploy --only firestore:rules,firestore:indexes --project <recette>`), **après autorisation**.
2. Signature release : keystore dans les secrets de CI, `minifyEnabled` testé, règles ProGuard vérifiées (Gson et Firestore).
3. Distribution en bêta fermée par Firebase App Distribution ou Play Console (test interne), à une liste nominative.
4. API mobile V2.5 v1, en lecture puis en écriture, puis retrait de Firestore.
5. Publication seulement après validation juridique et GO explicite.
