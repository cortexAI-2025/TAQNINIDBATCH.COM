# Notes de version — Taqnin ID Batch Android V1 bêta

Branche `claude/android-v1-securite`. APK de démonstration : artefact GitHub Actions `taqnin-id-batch-android-v1-beta`, version debug, paquet `com.taqnid.batch.debug`. **Aucune publication sur un store, aucun déploiement Firebase.**

## Le projet compile

Le code d'origine n'avait jamais pu compiler : dépôts Gradle, `gradle.properties` absent, ressources invalides, styles inexistants, convertisseurs Room vides, inclusion sans identifiant. Le wrapper Gradle 8.7 est ajouté.

## Sécurité

- Règles Firestore à privilèges minimaux (`firebase/firestore.rules`), testées sur l'émulateur avec 63 tests (4 rôles, 2 organisations).
- Fin de l'auto-attribution du rôle ADMIN. L'inscription crée un profil en attente ; le rattachement passe par `scripts/assign-user.js` (SDK Admin, tracé dans `/admin_audit`).
- Rôles appliqués dans l'interface et dans les dépôts locaux.
- Données locales effacées à la déconnexion et au changement de compte ; sauvegarde Android désactivée.
- Toutes les opérations sont attribuées à l'UID de leur auteur.

## Mode hors ligne et synchronisation

- Room v2 avec migration sans perte : plus de recréation destructive de la base.
- File d'attente persistante : chaque élément est marqué « envoyé » seulement après confirmation du serveur, et seulement s'il n'a pas changé pendant l'envoi.
- Lots versionnés : une modification concurrente devient un conflit visible, résolu en « garder la version serveur » ou « garder ma saisie ».
- Refus du serveur conservés avec leur motif ; nouvelles tentatives sans limite après une coupure réseau.
- Récupération des lots, actions et transferts de l'organisation (réinstallation, autre appareil).
- Indicateurs de connexion et de saisies en attente dans la barre d'outils.

## Fonctions métier

- Identifiants provisoires `TAQ-AAAA-XXXXXXXXXX` (50 bits) ; identifiants officiels web `MA-…` reconnus.
- Scanner : identifiant seul, URL de vérification web, QR JSON web, manifeste de transfert ; messages d'erreur clairs.
- Quantités, unités et transitions de stade contrôlées ; confirmation avant tout changement de stade.
- Transferts :
  - choix des lots dans la liste ;
  - sortie de stock atomique ;
  - double appui sans effet ;
  - réception par le destinataire, avec les quantités constatées (au plus la quantité expédiée) et création des lots reçus ;
  - refus du chargement possible.
- Exports CSV et PDF réparés (nom de fichier) et protégés contre l'injection de formules.

## Connu, à suivre

- **Pas de lien avec la plateforme web V2.5** : voir `ARCHITECTURE_INTEGRATION.md`.
- Textes en français dans le code ; l'arabe reste à faire (la mise en page RTL est déjà activée).
- iText 5 (licence AGPL) à remplacer avant distribution.
- La connexion réelle demande un projet Firebase de recette (secret `GOOGLE_SERVICES_JSON_RECETTE`).
