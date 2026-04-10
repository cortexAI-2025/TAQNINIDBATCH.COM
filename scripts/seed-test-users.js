/**
 * seed-test-users.js
 * Crée les 4 comptes utilisateurs de test (un par rôle) dans Firebase Auth + Firestore.
 *
 * Prérequis :
 *   npm install firebase-admin
 *
 * Utilisation :
 *   1. Télécharger la clé de service Firebase :
 *      Console Firebase → Paramètres du projet → Comptes de service → Générer une nouvelle clé privée
 *   2. Placer le fichier JSON téléchargé dans ce dossier et le renommer en serviceAccountKey.json
 *   3. node seed-test-users.js
 */

const admin = require("firebase-admin");
const path = require("path");
const fs = require("fs");

// ── Vérification de la clé de service ────────────────────────────────────────
const keyPath = path.join(__dirname, "serviceAccountKey.json");
if (!fs.existsSync(keyPath)) {
  console.error(
    "\n❌  serviceAccountKey.json introuvable dans scripts/\n" +
      "   → Console Firebase → Paramètres → Comptes de service → Générer une nouvelle clé privée\n"
  );
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(require(keyPath)),
});

const auth = admin.auth();
const db = admin.firestore();

// ── Définition des comptes de test ────────────────────────────────────────────
const TEST_PASSWORD = "test1234!";
const TEST_LICENSE_ID = "LIC-TEST-001";
const TEST_LICENSE_NAME = "Exploitation Test";

const TEST_USERS = [
  {
    email: "admin.test@taqnin.app",
    displayName: "Admin Test",
    role: "ADMIN",
    roleFr: "Administrateur",
  },
  {
    email: "manager.test@taqnin.app",
    displayName: "Manager Test",
    role: "MANAGER",
    roleFr: "Manager",
  },
  {
    email: "ouvrier.test@taqnin.app",
    displayName: "Ouvrier Test",
    role: "OUVRIER",
    roleFr: "Ouvrier",
  },
  {
    email: "lecteur.test@taqnin.app",
    displayName: "Lecteur Test",
    role: "LECTEUR",
    roleFr: "Lecteur",
  },
];

// ── Création des comptes ───────────────────────────────────────────────────────
async function seedUsers() {
  console.log("\n🌱  Création des comptes utilisateurs de test…\n");

  for (const u of TEST_USERS) {
    try {
      // 1. Créer (ou récupérer si déjà existant) le compte Firebase Auth
      let userRecord;
      try {
        userRecord = await auth.getUserByEmail(u.email);
        // Réinitialiser le mot de passe au cas où il aurait changé
        await auth.updateUser(userRecord.uid, {
          password: TEST_PASSWORD,
          displayName: u.displayName,
        });
        console.log(`  ↻  [${u.role}] ${u.email}  (compte existant mis à jour)`);
      } catch (notFoundErr) {
        if (notFoundErr.code !== "auth/user-not-found") throw notFoundErr;
        userRecord = await auth.createUser({
          email: u.email,
          password: TEST_PASSWORD,
          displayName: u.displayName,
          emailVerified: true,
        });
        console.log(`  ✓  [${u.role}] ${u.email}  (compte créé)`);
      }

      // 2. Écrire le profil dans Firestore /users/{uid}
      await db.collection("users").doc(userRecord.uid).set(
        {
          uid: userRecord.uid,
          email: u.email,
          displayName: u.displayName,
          role: u.role,
          licenseId: TEST_LICENSE_ID,
          licenseName: TEST_LICENSE_NAME,
          isActive: true,
          biometricEnabled: false,
          createdAt: Date.now(),
          lastLoginAt: Date.now(),
        },
        { merge: true }
      );
    } catch (err) {
      console.error(`  ✗  [${u.role}] ${u.email}  →  ${err.message}`);
    }
  }

  console.log("\n✅  Seed terminé.\n");
  console.log("┌─────────────────────────────────────────────────────────────┐");
  console.log("│  Comptes de test créés                                      │");
  console.log("├───────────────────┬───────────────────────────┬─────────────┤");
  console.log("│  Rôle             │  Email                    │  Mot de     │");
  console.log("│                   │                           │  passe      │");
  console.log("├───────────────────┼───────────────────────────┼─────────────┤");
  console.log("│  Administrateur   │  admin.test@taqnin.app    │  test1234!  │");
  console.log("│  Manager          │  manager.test@taqnin.app  │  test1234!  │");
  console.log("│  Ouvrier          │  ouvrier.test@taqnin.app  │  test1234!  │");
  console.log("│  Lecteur          │  lecteur.test@taqnin.app  │  test1234!  │");
  console.log("└───────────────────┴───────────────────────────┴─────────────┘\n");

  process.exit(0);
}

seedUsers().catch((err) => {
  console.error("Erreur fatale :", err);
  process.exit(1);
});
