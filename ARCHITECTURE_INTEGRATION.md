# Architecture d'intégration — Taqnin ID Batch Web V2.5 et Android V1

Document commun aux deux dépôts. Rédigé le 5 novembre 2026.

## 1. Principe

**La plateforme web V2.5 est la seule source de vérité réglementaire.** Elle seule fait foi pour :

- les organisations et leurs autorisations ANRAC ;
- les utilisateurs, leurs rôles et leurs habilitations (KYC) ;
- les lots et leurs identifiants officiels ;
- le registre des mouvements de stock, scellé SHA-256 ;
- les analyses, les contrats, les expéditions et réceptions, les destructions, les incidents ;
- les règles de conformité et le journal d'audit.

L'application Android est un **outil de terrain** : elle saisit hors ligne, garde ses saisies dans une file d'attente persistante, puis les transmet. Elle ne doit pas devenir une seconde base réglementaire concurrente.

Les deux architectures ne sont pas fusionnées. Le web reste en React, tRPC, Express, Drizzle et MySQL. Android reste en Kotlin MVVM, avec Room et WorkManager.

## 2. Situation actuelle (bêta)

```
Android (Room) ──WorkManager──▶ Firestore (projet de recette isolé)
Web V2.5 (React) ──tRPC──▶ Express ──Drizzle──▶ MySQL/MariaDB
```

Les deux systèmes **ne sont pas reliés**. En bêta fermée, Android fonctionne sur un projet Firebase de recette et avec des données fictives :

- les règles Firestore sont testées (`firebase/firestore.rules`) ;
- rien de ce qui est saisi sur Android n'a de valeur réglementaire.

C'est acceptable **uniquement en bêta**. Une mise en production sans intégration créerait deux sources de vérité pour les mêmes opérations, ce que la mission interdit.

## 3. Cible : API mobile versionnée portée par V2.5

```
Android (Room = cache + file d'attente)
   │  HTTPS, jeton d'accès court, clientRequestId par opération
   ▼
V2.5  /api/mobile/v1/*  ──▶  procédures existantes (contrôles, registre scellé, audit)
   ▼
MySQL (source unique)
```

### Règles de conception

1. **Aucune connexion directe d'Android à MySQL.**
2. **Authentification.** Android passe par le fournisseur OAuth de la plateforme (flux appareil ou PKCE). Le serveur délivre un jeton d'accès court et un jeton de renouvellement révocable. Le rôle et l'organisation viennent du serveur, jamais de l'appareil.
3. **Versionnage.** Le préfixe `/api/mobile/v1` est figé ; tout changement incompatible ouvre `v2`. Le serveur expose la version minimale d'application acceptée.
4. **Réutilisation des contrôles.** Chaque route mobile appelle les procédures tRPC existantes, par exemple :
   - `register.record`, `shipments.create` et `shipments.receive` ;
   - `incidents.declare` et `destructions.create` ;
   - `cultivation.create` et `postHarvest.create`.

   Elle ne réimplémente aucune règle. Les contrôles d'accès par rôle, organisation et relation au lot restent ceux de V2.5.
5. **Idempotence.** Chaque opération porte un `clientRequestId` (UUID) choisi sur l'appareil. V2.5 le gère déjà (table `idempotency_keys`) pour la cultivation, la post-récolte, l'incident, la destruction, l'expédition, la réception et le registre. Un rejeu après coupure rend la première réponse, sans doublon.
6. **Conflits.** Le registre ne s'écrase jamais : chaque mouvement est une ligne scellée. Un mouvement refusé (stock insuffisant, droits) revient à l'appareil avec son motif et reste visible. Il n'est jamais effacé.
7. **Lecture.** Android lit les lots, les transferts et les mouvements de son organisation par synchronisation incrémentale (`since`). Les vues partenaires restent réduites, comme sur le web : pas de GPS ni de journaux internes.

### Feuille de route

| Étape | Contenu | Prérequis |
|---|---|---|
| 0 — bêta (maintenant) | Android sur Firestore de recette, données fictives | Règles Firestore testées (fait) |
| 1 — API en lecture | `GET /api/mobile/v1/batches`, `/shipments`, `/me` ; Android affiche les lots officiels (MA-…) | Authentification mobile, revue de sécurité |
| 2 — API en écriture | Pesées et pertes vers `register.record` ; transferts vers `shipments.*` ; destructions et incidents | Correspondance des mouvements validée par le métier |
| 3 — retrait de Firestore | Android n'écrit plus que vers V2.5 ; Firestore supprimé ou réduit aux notifications | Migration des données de recette, recette complète |

## 4. Table de correspondance des modèles

| Android V1 | Web V2.5 | Écart / décision |
|---|---|---|
| `User.uid` (Firebase) | `users.openId` (OAuth) | Étape 1 : un seul fournisseur d'identité. |
| `User.role` ADMIN | `admin` (plateforme) ou responsable d'organisation | ADMIN Android est un administrateur **d'organisation** ; il n'a pas d'équivalent exact. Il faut un rôle d'organisation côté V2.5 ou un rattachement à `farmer`. |
| `User.role` MANAGER | `farmer` (coopérative), `auditor` (transformateur), `buyer` | Dépend du type d'organisation. |
| `User.role` OUVRIER | — | Absent de V2.5. Proposition : sous-rôle « opérateur de saisie », limité aux pesées et incidents. |
| `User.role` LECTEUR | consultation (`user` validé, sans écriture) | À formaliser côté V2.5. |
| `User.licenseId` | `organizations.anracRef` / `organizations.id` | La licence ANRAC est l'identifiant métier commun. |
| `User.isActive` | KYC approuvé (`kyc_requests.status`) et rôle ≠ `user` | Activation décidée côté serveur dans les deux cas. |
| `Batch.taqninId` provisoire `TAQ-AAAA-XXXXXXXXXX` | `batches.taqninId` officiel `MA-RÉGION-COOP-VARIÉTÉ-ANNÉE-NNNN` | Android **lit** les deux formats. Seul le serveur web attribue l'identifiant officiel. À l'étape 2, Android garde le provisoire comme référence locale et reçoit l'officiel. |
| `Batch.variety` (texte libre) | `genetics` (variétés certifiées, catalogue ANRAC, numéros ONSSA) | Android devra choisir dans le catalogue. Le texte libre n'est pas recevable réglementairement. |
| `Batch.initialQuantity` / `currentQuantity` / `unit` | Registre `stock_movements` en kg, par catégorie (semences, plants, récolte, produit, excédents) | V2.5 ne stocke pas de « quantité courante » de lot : le stock est la somme des mouvements. Android convertit en kg à l'envoi. |
| `Batch.currentStage` | `batches.currentStage` + `cultivations` + `post_harvest` | Les stades Android sont plus fins (germination, végétation…). Correspondance à valider par le métier. |
| `Batch.complianceStatus` | `lab_analyses.complianceStatus` (laboratoire désigné) | Jamais saisi sur Android (règle Firestore : un lot naît EN_ATTENTE). |
| `BatchAction` PESEE / AJUSTEMENT | `stock_movements` raison `correction` | |
| `BatchAction` PERTE | `incident_declarations` (perte, vol) + sortie `loss` | V2.5 impose la déclaration sous 24 h. |
| `BatchAction` RETRAIT_DESTRUCTION | `destructions` (procès-verbal, commission) | V2.5 exige un PV et une commission. Android doit les saisir avant envoi. |
| `BatchAction` RETRAIT_VENTE | `shipments` sous contrat | Toute vente de coopérative passe par une expédition sous contrat (V2.5). |
| `Transfer` (un manifeste, plusieurs lots) | `shipments` (une expédition par lot : bon de livraison, scellé, transporteur agréé) | Un manifeste Android donnera N expéditions V2.5. Transporteur agréé, scellé et contrat à saisir. |
| Réception d'un `Transfer` | `shipments.receive` (scellé intact, quantité reçue, écart) | Même logique : quantité reçue ≤ expédiée. |
| `Location` | `parcels` (pour la culture) ; entrepôts non modélisés | À compléter. |
| Journal `actions` Firestore (ajout seul) | `compliance_logs` + registre scellé | Le registre V2.5 fait foi. |

## 5. Ce qui n'est pas fait

- **L'API mobile `/api/mobile/v1`.** Elle n'existe pas encore. Elle demande une décision sur l'authentification mobile et une revue de sécurité dédiée.
- **Les correspondances de rôles et de stades.** Elles demandent une validation métier, et juridique pour les rôles.
- **Les données saisies sur Android pendant la bêta.** Elles ne seront **pas** migrées vers V2.5 : ce sont des données fictives de recette.
