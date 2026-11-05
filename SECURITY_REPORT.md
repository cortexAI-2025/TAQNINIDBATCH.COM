# Rapport de sécurité — Taqnin ID Batch Android V1

Branche `claude/android-v1-securite`, 5 novembre 2026.

**Rien n'a été déployé.** Les règles Firestore n'ont pas été envoyées sur un projet Firebase. Tous les tests ont tourné sur les émulateurs Firebase et en CI.

## 1. Règles Firestore (`firebase/firestore.rules`)

Principe : **tout est refusé par défaut**. Le rôle, l'organisation (`licenseId`) et l'activation sont lus dans `/users/{uid}`, et le client ne peut pas modifier ces champs.

| Exigence de la mission | Règle | Tests (émulateur) |
|---|---|---|
| 1. Pas d'auto-attribution du rôle administrateur | À l'inscription, le profil est créé `LECTEUR`, sans organisation, `isActive=false`. En mise à jour, un utilisateur ne change que `displayName`, `biometricEnabled` et `lastLoginAt`. Un ADMIN d'organisation peut nommer LECTEUR, OUVRIER ou MANAGER dans son organisation, jamais ADMIN, et jamais sur son propre compte. | 9 cas « élévation de privilèges » + 12 cas (4 rôles × rôle, organisation, activation) |
| 2. Pas de lecture des données d'une autre organisation | Lots, actions et emplacements sont lisibles seulement si leur `licenseNumber` / `orgId` vaut l'organisation de l'appelant. Un transfert n'est lisible que par l'expéditeur et le destinataire. Une requête non filtrée par organisation est refusée. | 11 cas « isolation », 1 cas « tiers » sur les transferts |
| 3. Droits vérifiés côté serveur | Toutes les vérifications sont dans les règles. Les contrôles du client ne servent qu'au confort. | Ensemble des 63 tests |
| 4. `role`, `licenseId`, `isActive` et affiliations protégés | Voir 1. Le rattachement passe uniquement par `scripts/assign-user.js` (SDK Admin), qui trace chaque changement dans `/admin_audit`. Aucune règle n'ouvre cette collection. | Tests des règles ; outil vérifié sur les émulateurs Auth et Firestore |
| 5. Documents sensibles protégés | Profils lisibles seulement par leur titulaire et par l'ADMIN de l'organisation. Collections non listées refusées. Aucune suppression nulle part. | « aucun profil, lot, action ou transfert ne peut être supprimé » |
| 6. Opérations attribuables | Lot : `createdBy` et `updatedBy` égaux à l'UID. Action : `performedBy` égal à l'UID. Transfert : `createdBy` et `receivedBy` égaux à l'UID. | « une action est attribuée à son auteur réel », « une mise à jour doit être attribuée à son auteur » |
| 7. Journaux non effaçables ni réécrivables | Les actions se créent sans pouvoir être modifiées ni supprimées, même par un ADMIN. Seul le rejeu identique est accepté, pour l'idempotence. | « une action ne peut être ni réécrite ni effacée, même par l'admin » |

Contrôles métier supplémentaires dans les règles :

- un lot naît « en attente » : l'application ne peut jamais déclarer un lot conforme ;
- les quantités sont positives et bornées, les unités appartiennent à une liste fermée ;
- l'organisation, la quantité initiale et la conformité d'un lot sont immuables ;
- une mise à jour doit partir de la dernière version, sinon c'est un conflit, jamais un écrasement ;
- un transfert ne peut pas viser sa propre organisation ; il est réceptionné une seule fois, par le destinataire ;
- l'expéditeur peut annuler un transfert tant que rien n'est reçu.

## 2. Application

| Mesure | Fichier |
|---|---|
| L'inscription et le premier accès créent un profil en attente ; la connexion est refusée tant que le compte n'est ni actif ni rattaché | `repository/UserRepository.kt` |
| Le rôle et l'organisation sont toujours relus sur le serveur à chaque synchronisation ; un compte désactivé suspend l'envoi | `worker/SyncWorker.kt` |
| Les boutons suivent le rôle ; le dépôt local refait les contrôles avant d'écrire | `ui/**`, `repository/*Repository.kt` |
| La base locale est effacée à la déconnexion et quand un autre compte se connecte | `UserRepository.switchLocalAccount`, `logout` |
| La déconnexion prévient s'il reste des saisies non envoyées | `ui/profile/ProfileFragment.kt` |
| Sauvegarde Android désactivée (`allowBackup=false`, `dataExtractionRules` excluant tout) | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| Le trafic en clair est interdit (`usesCleartextTraffic=false`, network security config) | inchangé, vérifié |
| Un profil sans `isActive` est considéré inactif (auparavant : actif) | `data/remote/FirebaseRepository.kt` |

## 3. Secrets

- `app/google-services.json` est un fichier **factice**, sans clé réelle. La CI peut le remplacer par le secret facultatif `GOOGLE_SERVICES_JSON_RECETTE`, jamais commité.
- `scripts/serviceAccountKey.json` est ignoré par git.
- `seed-test-users.js` refuse désormais un projet dont l'identifiant n'évoque pas la recette (`recette`, `test`, `demo`, `staging`).

## 4. Risques résiduels

| Risque | Gravité | Recommandation |
|---|---|---|
| Un transfert déclare des `batchIds` que les règles ne peuvent pas vérifier un à un (pas de boucle en règles Firestore). L'application les contrôle (lots de l'organisation), mais un client modifié pourrait citer un lot étranger dans **son** manifeste. Il ne pourrait ni le lire ni le modifier. | Faible | Création des transferts par une Cloud Function, ou directement via l'API V2.5 (cible). |
| Les règles relisent `/users/{uid}` à chaque requête, ce qui coûte une lecture facturée. | Info | Custom claims (rôle, licence) posés par le SDK Admin. |
| Le mot de passe minimal est de 6 caractères (Firebase par défaut). Il n'y a ni double authentification ni App Check. | Moyenne | Politique de mot de passe et App Check (Play Integrity) avant toute ouverture au-delà de la bêta fermée. |
| La biométrie déverrouille l'application localement, sans réauthentifier Firebase. | Faible | Acceptable pour une bêta. Jeton de session court ensuite. |
| iText 5 (`itextg`) est sous licence AGPL et ancien. | Faible (licence) | Remplacer avant distribution. |
| Deux sources de vérité tant qu'Android n'est pas relié à V2.5. | Élevée pour la production | Voir `ARCHITECTURE_INTEGRATION.md`. C'est un **NO-GO production** tant que ce n'est pas fait. |
