# Taqnin ID Batch — Application Android de traçabilité réglementaire

Application Android native de traçabilité **seed to sale** (graine → vente) basée sur le modèle METRC,
développée en Kotlin avec architecture MVVM, Room, Firebase et CameraX.

---

## Table des matières

1. [Prérequis](#prérequis)
2. [Structure du projet](#structure-du-projet)
3. [Configuration Firebase](#configuration-firebase)
4. [Importer dans Android Studio](#importer-dans-android-studio)
5. [Générer l'APK](#générer-lapk)
6. [Architecture technique](#architecture-technique)
7. [Fonctionnalités](#fonctionnalités)
8. [Rôles utilisateurs](#rôles-utilisateurs)
9. [Mode hors-ligne](#mode-hors-ligne)
10. [Format Taqnin ID](#format-taqnin-id)

---

## Prérequis

| Outil | Version minimale |
|-------|-----------------|
| Android Studio | Hedgehog (2023.1) ou plus récent |
| JDK | 17 |
| Android SDK | API 26 (Android 8.0) minimum — API 34 cible |
| Gradle | 8.2 (géré automatiquement par Wrapper) |
| Compte Firebase | Gratuit (Spark plan) |

---

## Structure du projet

```
TaqninIDBatch/
├── app/
│   ├── src/main/
│   │   ├── java/com/taqnid/batch/
│   │   │   ├── TaqninApplication.kt          # Application class
│   │   │   ├── MainActivity.kt               # Hôte de navigation
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── AppDatabase.kt        # Room Database (version 1)
│   │   │   │   │   ├── dao/                  # DAOs: Batch, Action, Transfer, Location
│   │   │   │   │   └── entity/               # Entités Room (mapping DB ↔ modèles)
│   │   │   │   ├── model/                    # Data classes métier
│   │   │   │   │   ├── Batch.kt              # Lot + BatchStage + ComplianceStatus
│   │   │   │   │   ├── BatchAction.kt        # Actions / Timeline
│   │   │   │   │   ├── Transfer.kt           # Manifeste de transfert
│   │   │   │   │   ├── User.kt               # Utilisateur + UserRole
│   │   │   │   │   └── Location.kt           # Emplacement physique
│   │   │   │   └── remote/
│   │   │   │       └── FirebaseRepository.kt # Firestore cloud sync
│   │   │   ├── repository/
│   │   │   │   ├── BatchRepository.kt        # Logique métier lots
│   │   │   │   ├── TransferRepository.kt     # Logique métier transferts
│   │   │   │   └── UserRepository.kt         # Auth Firebase + cache local
│   │   │   ├── ui/
│   │   │   │   ├── auth/                     # LoginActivity + LoginViewModel
│   │   │   │   ├── home/                     # HomeFragment (tableau de bord)
│   │   │   │   ├── batch/                    # Liste, Détail, Création + Adapters
│   │   │   │   ├── scanner/                  # CameraX + ML Kit + ScanOverlayView
│   │   │   │   ├── transfer/                 # Liste, Détail, Création + Adapters
│   │   │   │   ├── reports/                  # Génération PDF/CSV
│   │   │   │   └── profile/                  # Profil + biométrie + déconnexion
│   │   │   ├── worker/
│   │   │   │   └── SyncWorker.kt             # WorkManager (synchro Firebase offline)
│   │   │   └── utils/
│   │   │       ├── TaqninIdGenerator.kt      # Génération des IDs TAQ-XXXX-YYYY
│   │   │       ├── QRCodeGenerator.kt        # ZXing — génération QR codes
│   │   │       ├── PdfExporter.kt            # iTextG — export rapports PDF
│   │   │       └── CsvExporter.kt            # Export CSV (lots, actions, transferts)
│   │   ├── res/
│   │   │   ├── layout/                       # Tous les layouts XML
│   │   │   ├── navigation/nav_graph.xml      # Graphe de navigation
│   │   │   ├── menu/bottom_nav_menu.xml      # Menu BottomNavigationView
│   │   │   ├── drawable/                     # Icônes Material Design + formes
│   │   │   ├── values/                       # strings, colors, themes, dimens
│   │   │   └── xml/                          # network_security_config, file_paths
│   │   └── AndroidManifest.xml
│   ├── build.gradle                          # Dépendances du module app
│   ├── google-services.json                  # ← À remplacer ! (voir ci-dessous)
│   └── proguard-rules.pro
├── build.gradle                              # Configuration racine
├── settings.gradle
└── README.md
```

---

## Configuration Firebase

**Cette étape est obligatoire** pour que l'authentification et la synchronisation cloud fonctionnent.

### Étape 1 — Créer le projet Firebase

1. Allez sur [https://console.firebase.google.com](https://console.firebase.google.com)
2. Cliquez sur **Créer un projet** → donnez-lui un nom (ex : `taqnin-id-batch`)
3. Désactivez Google Analytics si non nécessaire → **Créer le projet**

### Étape 2 — Ajouter l'application Android

1. Dans le projet Firebase, cliquez sur l'icône **Android** (➕ Ajouter une application)
2. **Nom du package Android** : `com.taqnid.batch`
3. **Surnom de l'appli** : `Taqnin ID Batch`
4. Cliquez sur **Enregistrer l'application**
5. **Téléchargez** le fichier `google-services.json`
6. **Remplacez** le fichier placeholder `app/google-services.json` par le fichier téléchargé

### Étape 3 — Activer Firebase Authentication

1. Dans la Firebase Console → **Authentication** → **Commencer**
2. Onglet **Sign-in method** → Activez **E-mail/Mot de passe**
3. Sauvegardez

### Étape 4 — Configurer Firestore

1. Dans la Firebase Console → **Firestore Database** → **Créer une base de données**
2. Choisissez le mode **Production** (ou Test pour le développement)
3. Sélectionnez une région proche de vos utilisateurs
4. Ajoutez ces règles de sécurité dans l'onglet **Règles** :

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Seuls les utilisateurs authentifiés peuvent lire/écrire
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Étape 5 — Créer le premier utilisateur Admin

Depuis la Firebase Console → Authentication → **Ajouter un utilisateur** :
- Email : `admin@votre-licence.com`
- Mot de passe : (choisissez un mot de passe fort)

Puis, dans Firestore → collection `users` → ajoutez un document avec l'UID de cet utilisateur :
```json
{
  "uid": "<UID_FIREBASE>",
  "email": "admin@votre-licence.com",
  "displayName": "Administrateur",
  "role": "ADMIN",
  "licenseId": "LIC-001",
  "licenseName": "Licence Principale",
  "isActive": true,
  "biometricEnabled": false
}
```

---

## Importer dans Android Studio

### Méthode 1 — Depuis l'interface

1. Ouvrez **Android Studio**
2. Cliquez sur **File → Open** (ou **Open** sur l'écran d'accueil)
3. Naviguez jusqu'au dossier `TaqninIDBatch/`
4. Cliquez sur **OK** → attendez la synchronisation Gradle (2-5 min la première fois)

### Méthode 2 — Depuis le terminal

```bash
# Ouvrir directement depuis la ligne de commande
studio TaqninIDBatch/
# ou
open -a "Android Studio" TaqninIDBatch/  # macOS
```

### Synchronisation Gradle

Si Gradle ne se synchronise pas automatiquement :
- Cliquez sur **File → Sync Project with Gradle Files**
- ou appuyez sur l'icône 🔄 dans la barre d'outils

---

## Générer l'APK

### APK de débogage (développement)

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

L'APK sera généré dans :
```
app/build/outputs/apk/debug/app-debug.apk
```

### APK de release (production)

1. **Build → Generate Signed Bundle / APK**
2. Choisissez **APK**
3. Créez ou sélectionnez un **Keystore** (gardez-le précieusement !)
4. Renseignez les alias et mots de passe
5. Choisissez le variant **release**
6. Cliquez sur **Finish**

L'APK signé sera dans :
```
app/build/outputs/apk/release/app-release.apk
```

### Via la ligne de commande

```bash
# Debug
./gradlew assembleDebug

# Release (nécessite un keystore configuré)
./gradlew assembleRelease

# Bundle AAB pour le Play Store
./gradlew bundleRelease
```

### Installer sur un appareil connecté

```bash
./gradlew installDebug
# ou
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Architecture technique

```
┌─────────────────────────────────────────────────────────┐
│                       UI Layer                          │
│    Activities / Fragments / Adapters / Custom Views     │
└──────────────────────┬──────────────────────────────────┘
                       │ observe LiveData / StateFlow
┌──────────────────────▼──────────────────────────────────┐
│                   ViewModel Layer                       │
│    BatchViewModel, TransferViewModel, LoginViewModel…   │
└──────────────────────┬──────────────────────────────────┘
                       │ suspend functions / Flow
┌──────────────────────▼──────────────────────────────────┐
│                  Repository Layer                       │
│    BatchRepository, TransferRepository, UserRepository  │
└──────┬───────────────────────────────┬──────────────────┘
       │ Room (offline)                │ Firebase (online)
┌──────▼──────┐                 ┌──────▼──────────────────┐
│  Room DB    │                 │  Firebase Firestore      │
│  (SQLite)   │◄── SyncWorker ──│  (synchronisation)      │
└─────────────┘                 └─────────────────────────┘
```

### Technologies utilisées

| Composant | Bibliothèque | Version |
|-----------|-------------|---------|
| Langage | Kotlin | 1.9.22 |
| Base de données locale | Room (SQLite) | 2.6.1 |
| Architecture | MVVM + LiveData/Flow | - |
| Navigation | Navigation Component | 2.7.7 |
| Cloud sync | Firebase Firestore | BoM 32.7.2 |
| Authentification | Firebase Auth | BoM 32.7.2 |
| Biométrie | BiometricPrompt | 1.1.0 |
| Scan QR | CameraX + ML Kit | 1.3.1 / 17.2.0 |
| Génération QR | ZXing | 4.3.0 |
| Export PDF | iTextG | 5.5.10 |
| Synchro offline | WorkManager | 2.9.0 |
| UI | Material Design 3 | 1.11.0 |

---

## Fonctionnalités

### Gestion des lots (Batch)
- Création avec Taqnin ID automatique (`TAQ-XXXX-YYYY`)
- Suivi des 9 stades : Germination → Végétation → Floraison → Récolte → Séchage → Curing → Transformation → Emballage → Vente
- Enregistrement des actions : pesées, pertes, ajouts, traitements
- Timeline verticale complète de toutes les actions
- Indicateur de conformité (vert / orange / rouge)
- Génération du QR code individuel du lot

### Scan de codes-barres / QR codes
- Scanner temps réel avec CameraX + ML Kit
- Reconnaissance des Taqnin ID individuels
- Reconnaissance des QR de manifestes de transfert
- Vue de visée animée avec retour visuel (succès / erreur)
- Lampe torche intégrée

### Transferts entre entités
- Création d'un manifeste multi-lots
- Génération d'un QR code de transport
- Validation à la réception avec contrôle des quantités
- Calcul et affichage des écarts (discrepancies)
- Export PDF du manifeste pour le chauffeur

### Inventaire et conformité
- Stock en temps réel par lot
- Alertes lots expirant dans les 7 prochains jours
- Alertes stock bas (seuil configurable)
- Calcul du taux de perte (initial vs actuel)

### Rapports réglementaires
- Export PDF : rapport de lots, rapport de transferts, manifeste individuel
- Export CSV : lots, actions, transferts (compatible Excel avec BOM UTF-8)
- Sélection de période par calendrier Material
- Statistiques résumées de la période

### Multi-utilisateurs et rôles
| Rôle | Niveau | Droits |
|------|--------|--------|
| Lecteur | 0 | Consultation uniquement |
| Ouvrier | 1 | Scan + pesées |
| Manager | 2 | Création + transferts + export |
| Administrateur | 3 | Tous droits |

### Authentification
- Connexion email / mot de passe (Firebase Auth)
- Authentification biométrique (empreinte digitale)
- Persistance de session

---

## Mode hors-ligne

L'application fonctionne **intégralement sans connexion internet** grâce à Room (SQLite).

### Mécanisme de synchronisation

1. Toutes les opérations écrivent d'abord en **Room local** avec `isSynced = false`
2. **SyncWorker** (WorkManager) se déclenche dès qu'une connexion réseau est détectée
3. Les entités non synchronisées sont envoyées vers **Firebase Firestore**
4. Chaque entité est marquée `isSynced = true` après upload réussi
5. En cas d'échec réseau : retry exponentiel (10s → 20s → 40s, max 3 tentatives)
6. Synchronisation périodique automatique toutes les **15 minutes**

### Données stockées localement

- Tous les lots (`batches`)
- Toutes les actions / timeline (`batch_actions`)
- Tous les transferts (`transfers`)
- Tous les emplacements (`locations`)
- Préférences utilisateur (SharedPreferences)

---

## Format Taqnin ID

```
TAQ-XXXX-YYYY
 │    │    │
 │    │    └── Année de création (4 chiffres)
 │    └──────── 4 caractères alphanumériques aléatoires (A-Z, 0-9)
 └───────────── Préfixe fixe "TAQ"
```

**Exemples valides :**
- `TAQ-A3F2-2024`
- `TAQ-XK9M-2025`
- `TAQ-0Z7P-2026`

**Regex de validation :** `^TAQ-[A-Z0-9]{4}-\d{4}$`

---

## Dépannage

### Erreur "google-services.json not found"
→ Remplacez `app/google-services.json` par votre vrai fichier Firebase.

### Erreur "INSTALL_FAILED_USER_RESTRICTED"
→ Activez l'installation depuis des sources inconnues dans les paramètres Android.

### La caméra ne fonctionne pas
→ Vérifiez que la permission CAMERA est accordée dans les paramètres de l'application.

### "Failed to connect to Firebase"
→ Vérifiez la connexion internet et que le `google-services.json` est correct.

### Gradle sync échoue
→ Vérifiez la connexion internet. Essayez **File → Invalidate Caches and Restart**.

---

## Licence

© 2024 Taqnin ID Batch — Application de traçabilité réglementaire.
Tous droits réservés.
