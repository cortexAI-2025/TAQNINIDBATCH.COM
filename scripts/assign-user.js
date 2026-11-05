/**
 * assign-user.js — opération privilégiée : rattacher un compte à une
 * organisation, lui donner un rôle et l'activer (ou le désactiver).
 *
 * L'application ne peut jamais le faire elle-même : les règles Firestore
 * interdisent au client de modifier role, licenseId et isActive. Seul un
 * administrateur de la plateforme, avec une clé de service, passe par ici.
 *
 * Utilisation :
 *   node assign-user.js <email> <ADMIN|MANAGER|OUVRIER|LECTEUR> <licenseId> "<Nom de l'organisation>"
 *   node assign-user.js <email> --desactiver
 *
 * Sur l'émulateur (aucune clé requise) :
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 \
 *     GCLOUD_PROJECT=demo-taqnin node assign-user.js …
 *
 * Chaque changement est tracé dans /admin_audit (lisible et modifiable par
 * personne depuis l'application : aucune règle ne l'ouvre).
 */
const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

const ROLES = ["ADMIN", "MANAGER", "OUVRIER", "LECTEUR"];
const [email, roleOrFlag, licenseId, licenseName] = process.argv.slice(2);

function usage(message) {
  if (message) console.error(`\n❌  ${message}`);
  console.error('\nUsage : node assign-user.js <email> <rôle> <licenseId> "<organisation>"');
  console.error("        node assign-user.js <email> --desactiver\n");
  process.exit(1);
}

if (!email) usage("E-mail manquant");
const deactivate = roleOrFlag === "--desactiver";
if (!deactivate) {
  if (!ROLES.includes(roleOrFlag)) usage(`Rôle inconnu : ${roleOrFlag}`);
  if (!licenseId || !/^[A-Za-z0-9/_-]{2,60}$/.test(licenseId)) usage("Licence (licenseId) invalide");
}

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;
if (onEmulator) {
  admin.initializeApp({ projectId: process.env.GCLOUD_PROJECT || "demo-taqnin" });
} else {
  const keyPath = path.join(__dirname, "serviceAccountKey.json");
  if (!fs.existsSync(keyPath)) usage("serviceAccountKey.json introuvable (jamais commité : voir .gitignore)");
  admin.initializeApp({ credential: admin.credential.cert(require(keyPath)) });
}

(async () => {
  const user = await admin.auth().getUserByEmail(email);
  const ref = admin.firestore().collection("users").doc(user.uid);
  const before = (await ref.get()).data() || {};
  const change = deactivate
    ? { isActive: false }
    : { role: roleOrFlag, licenseId, licenseName: licenseName || licenseId, isActive: true };
  await ref.set({ uid: user.uid, email, ...change }, { merge: true });
  await admin.firestore().collection("admin_audit").add({
    at: Date.now(),
    operator: process.env.USER || "inconnu",
    target: user.uid,
    email,
    before: { role: before.role ?? null, licenseId: before.licenseId ?? null, isActive: before.isActive ?? null },
    after: change,
  });
  console.log(`✓ ${email} : ${JSON.stringify(change)}${onEmulator ? " (émulateur)" : ""}`);
  process.exit(0);
})().catch((err) => {
  console.error("Erreur :", err.message);
  process.exit(1);
});
