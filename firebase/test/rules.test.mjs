/**
 * Tests des règles Firestore sur l'émulateur.
 *
 * Deux organisations (ORG-A, ORG-B), chacune avec un ADMIN, un MANAGER, un
 * OUVRIER et un LECTEUR, plus un compte inactif et un compte sans organisation.
 * Chaque test part d'une base vide remplie avec les droits administrateur
 * (comme le ferait l'outil SDK Admin), puis agit au nom d'un utilisateur.
 */
import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, writeBatch, collection, getDocs, query, where } from "firebase/firestore";

let env;

const USERS = {
  adminA: { role: "ADMIN", licenseId: "ORG-A" },
  managerA: { role: "MANAGER", licenseId: "ORG-A" },
  ouvrierA: { role: "OUVRIER", licenseId: "ORG-A" },
  lecteurA: { role: "LECTEUR", licenseId: "ORG-A" },
  adminB: { role: "ADMIN", licenseId: "ORG-B" },
  managerB: { role: "MANAGER", licenseId: "ORG-B" },
  lecteurB: { role: "LECTEUR", licenseId: "ORG-B" },
  inactifA: { role: "MANAGER", licenseId: "ORG-A", isActive: false },
  sansOrg: { role: "MANAGER", licenseId: "" },
};

function profile(uid, { role, licenseId, isActive = true }) {
  return {
    uid,
    email: `${uid}@test.taqnin`,
    displayName: uid,
    role,
    licenseId,
    licenseName: licenseId ? `Organisation ${licenseId}` : "",
    isActive,
    biometricEnabled: false,
    createdAt: 1,
    lastLoginAt: 1,
  };
}

function batchDoc(taqninId, org, by, extra = {}) {
  return {
    id: `id-${taqninId}`,
    taqninId,
    variety: "Beldiya",
    type: "GRAINE",
    initialQuantity: 1000,
    currentQuantity: 1000,
    unit: "g",
    locationId: "",
    locationName: "",
    currentStage: "GERMINATION",
    complianceStatus: "EN_ATTENTE",
    ownerId: by,
    ownerName: by,
    licenseNumber: org,
    createdBy: by,
    updatedBy: by,
    version: 1,
    startDate: 1,
    lastUpdated: 1,
    expirationDate: null,
    notes: "",
    ...extra,
  };
}

function actionDoc(id, taqninId, org, by, extra = {}) {
  return {
    id,
    batchId: `id-${taqninId}`,
    taqninId,
    orgId: org,
    actionType: "PESEE",
    performedBy: by,
    performedByName: by,
    timestamp: 2,
    quantityBefore: 1000,
    quantityAfter: 950,
    quantityDelta: -50,
    unit: "g",
    stageFrom: null,
    stageTo: null,
    description: "Pesée",
    locationId: "",
    ...extra,
  };
}

function transferDoc(id, from, to, by, extra = {}) {
  return {
    id,
    transferNumber: `TRF-${id}`,
    status: "EN_TRANSIT",
    originLicenseId: from,
    originLicenseName: from,
    destinationLicenseId: to,
    destinationLicenseName: to,
    batchIds: ["TAQ-2026-AAAAAAAAAA"],
    batchSummaries: [{ taqninId: "TAQ-2026-AAAAAAAAAA", quantity: 100, unit: "g" }],
    driverName: "Chauffeur",
    vehiclePlate: "12345-A-1",
    qrCodeContent: "",
    createdBy: by,
    createdByName: by,
    createdAt: 3,
    dispatchedAt: 3,
    receivedAt: null,
    receivedBy: "",
    notes: "",
    ...extra,
  };
}

const as = (uid) => env.authenticatedContext(uid, { email: `${uid}@test.taqnin` }).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-taqnin",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

afterAll(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (db) => {
    for (const [uid, u] of Object.entries(USERS)) await setDoc(doc(db, "users", uid), profile(uid, u));
    await setDoc(doc(db, "batches", "TAQ-2026-AAAAAAAAAA"), batchDoc("TAQ-2026-AAAAAAAAAA", "ORG-A", "managerA"));
    await setDoc(doc(db, "batches", "TAQ-2026-BBBBBBBBBB"), batchDoc("TAQ-2026-BBBBBBBBBB", "ORG-B", "managerB"));
    await setDoc(doc(db, "actions", "act-a"), actionDoc("act-a", "TAQ-2026-AAAAAAAAAA", "ORG-A", "ouvrierA"));
    await setDoc(doc(db, "transfers", "trf-ab"), transferDoc("trf-ab", "ORG-A", "ORG-B", "managerA"));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("élévation de privilèges", () => {
  it("un nouvel inscrit ne peut pas se créer ADMIN", async () => {
    await assertFails(setDoc(doc(as("nouveau"), "users", "nouveau"), profile("nouveau", { role: "ADMIN", licenseId: "" })));
  });

  it("un nouvel inscrit ne peut pas se rattacher lui-même à une organisation", async () => {
    await assertFails(setDoc(doc(as("nouveau"), "users", "nouveau"), profile("nouveau", { role: "LECTEUR", licenseId: "ORG-A" })));
  });

  it("un nouvel inscrit ne peut pas s'activer lui-même", async () => {
    await assertFails(setDoc(doc(as("nouveau"), "users", "nouveau"), profile("nouveau", { role: "LECTEUR", licenseId: "", isActive: true })));
  });

  it("un nouvel inscrit crée un profil en attente (LECTEUR, sans organisation, inactif)", async () => {
    await assertSucceeds(
      setDoc(doc(as("nouveau"), "users", "nouveau"), profile("nouveau", { role: "LECTEUR", licenseId: "", isActive: false }))
    );
  });

  it("personne ne crée le profil d'un autre", async () => {
    await assertFails(setDoc(doc(as("managerA"), "users", "autre"), profile("autre", { role: "LECTEUR", licenseId: "", isActive: false })));
  });

  for (const uid of ["lecteurA", "ouvrierA", "managerA", "adminA"]) {
    it(`${uid} ne peut pas changer son propre rôle`, async () => {
      // Vers ADMIN, ou pour l'admin, vers un autre rôle : tout changement est refusé
      await assertFails(updateDoc(doc(as(uid), "users", uid), { role: uid === "adminA" ? "MANAGER" : "ADMIN" }));
    });
    it(`${uid} ne peut pas changer sa propre organisation`, async () => {
      await assertFails(updateDoc(doc(as(uid), "users", uid), { licenseId: "ORG-B" }));
    });
    it(`${uid} ne peut pas réactiver ou modifier son état`, async () => {
      await assertFails(updateDoc(doc(as(uid), "users", uid), { isActive: false }));
    });
  }

  it("un compte peut modifier ses préférences", async () => {
    await assertSucceeds(updateDoc(doc(as("ouvrierA"), "users", "ouvrierA"), { biometricEnabled: true, lastLoginAt: 99 }));
  });

  it("un compte inactif ne peut pas se réactiver", async () => {
    await assertFails(updateDoc(doc(as("inactifA"), "users", "inactifA"), { isActive: true }));
  });

  it("l'admin promeut un membre de son organisation jusqu'à MANAGER", async () => {
    await assertSucceeds(updateDoc(doc(as("adminA"), "users", "lecteurA"), { role: "MANAGER" }));
  });

  it("l'admin ne nomme pas d'autre ADMIN (outil SDK Admin seulement)", async () => {
    await assertFails(updateDoc(doc(as("adminA"), "users", "managerA"), { role: "ADMIN" }));
  });

  it("l'admin ne change pas l'organisation d'un membre", async () => {
    await assertFails(updateDoc(doc(as("adminA"), "users", "managerA"), { licenseId: "ORG-B" }));
  });

  it("l'admin ne touche pas aux comptes d'une autre organisation", async () => {
    await assertFails(updateDoc(doc(as("adminA"), "users", "lecteurB"), { role: "MANAGER" }));
    await assertFails(getDoc(doc(as("adminA"), "users", "lecteurB")));
  });

  it("un manager ne modifie pas le rôle d'un autre", async () => {
    await assertFails(updateDoc(doc(as("managerA"), "users", "lecteurA"), { role: "OUVRIER" }));
  });

  it("aucun profil ne peut être supprimé", async () => {
    await assertFails(deleteDoc(doc(as("adminA"), "users", "lecteurA")));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("isolation des organisations", () => {
  it("un membre lit les lots de son organisation", async () => {
    await assertSucceeds(getDoc(doc(as("lecteurA"), "batches", "TAQ-2026-AAAAAAAAAA")));
  });

  for (const uid of ["lecteurA", "ouvrierA", "managerA", "adminA"]) {
    it(`${uid} ne lit pas un lot d'une autre organisation`, async () => {
      await assertFails(getDoc(doc(as(uid), "batches", "TAQ-2026-BBBBBBBBBB")));
    });
  }

  it("une requête sur tous les lots est refusée ; filtrée sur l'organisation, elle passe", async () => {
    await assertFails(getDocs(collection(as("managerA"), "batches")));
    await assertSucceeds(getDocs(query(collection(as("managerA"), "batches"), where("licenseNumber", "==", "ORG-A"))));
  });

  it("un membre ne lit pas les actions d'une autre organisation", async () => {
    await assertFails(getDoc(doc(as("managerB"), "actions", "act-a")));
  });

  it("un manager ne modifie pas un lot d'une autre organisation", async () => {
    await assertFails(
      updateDoc(doc(as("managerB"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentQuantity: 1,
        version: 2,
        updatedBy: "managerB",
        lastUpdated: 5,
      })
    );
  });

  it("un manager ne crée pas un lot au nom d'une autre organisation", async () => {
    await assertFails(setDoc(doc(as("managerA"), "batches", "TAQ-2026-CCCCCCCCCC"), batchDoc("TAQ-2026-CCCCCCCCCC", "ORG-B", "managerA")));
  });

  it("un manager n'écrase pas un lot existant d'une autre organisation par création", async () => {
    await assertFails(setDoc(doc(as("managerA"), "batches", "TAQ-2026-BBBBBBBBBB"), batchDoc("TAQ-2026-BBBBBBBBBB", "ORG-A", "managerA")));
  });

  it("un compte inactif ou sans organisation ne lit rien", async () => {
    await assertFails(getDoc(doc(as("inactifA"), "batches", "TAQ-2026-AAAAAAAAAA")));
    await assertFails(getDoc(doc(as("sansOrg"), "batches", "TAQ-2026-AAAAAAAAAA")));
  });

  it("sans connexion, rien n'est lisible", async () => {
    await assertFails(getDoc(doc(anonymous(), "batches", "TAQ-2026-AAAAAAAAAA")));
    await assertFails(getDoc(doc(anonymous(), "users", "adminA")));
  });

  it("une action ne s'enregistre pas sur le lot d'une autre organisation", async () => {
    await assertFails(
      setDoc(doc(as("managerA"), "actions", "act-x"), actionDoc("act-x", "TAQ-2026-BBBBBBBBBB", "ORG-A", "managerA"))
    );
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("droits par rôle sur les lots", () => {
  it("LECTEUR et OUVRIER ne créent pas de lot", async () => {
    await assertFails(setDoc(doc(as("lecteurA"), "batches", "TAQ-2026-DDDDDDDDDD"), batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "lecteurA")));
    await assertFails(setDoc(doc(as("ouvrierA"), "batches", "TAQ-2026-DDDDDDDDDD"), batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "ouvrierA")));
  });

  it("MANAGER crée un lot dans son organisation", async () => {
    await assertSucceeds(setDoc(doc(as("managerA"), "batches", "TAQ-2026-DDDDDDDDDD"), batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "managerA")));
  });

  it("un lot ne naît pas conforme : la conformité ne vient pas de l'application", async () => {
    await assertFails(
      setDoc(
        doc(as("managerA"), "batches", "TAQ-2026-DDDDDDDDDD"),
        batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "managerA", { complianceStatus: "CONFORME" })
      )
    );
  });

  it("une quantité négative ou une unité inconnue est refusée", async () => {
    await assertFails(
      setDoc(doc(as("managerA"), "batches", "TAQ-2026-DDDDDDDDDD"), batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "managerA", { initialQuantity: -5, currentQuantity: -5 }))
    );
    await assertFails(
      setDoc(doc(as("managerA"), "batches", "TAQ-2026-DDDDDDDDDD"), batchDoc("TAQ-2026-DDDDDDDDDD", "ORG-A", "managerA", { unit: "tonnes" }))
    );
  });

  it("OUVRIER enregistre une pesée depuis la dernière version", async () => {
    await assertSucceeds(
      updateDoc(doc(as("ouvrierA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentQuantity: 950,
        version: 2,
        updatedBy: "ouvrierA",
        lastUpdated: 5,
      })
    );
  });

  it("OUVRIER ne change pas le stade (réservé au manager)", async () => {
    await assertFails(
      updateDoc(doc(as("ouvrierA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentStage: "FLORAISON",
        version: 2,
        updatedBy: "ouvrierA",
        lastUpdated: 5,
      })
    );
  });

  it("LECTEUR ne modifie rien", async () => {
    await assertFails(
      updateDoc(doc(as("lecteurA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentQuantity: 1,
        version: 2,
        updatedBy: "lecteurA",
        lastUpdated: 5,
      })
    );
  });

  it("MANAGER change le stade depuis la dernière version", async () => {
    await assertSucceeds(
      updateDoc(doc(as("managerA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentStage: "VEGETATION",
        version: 2,
        updatedBy: "managerA",
        lastUpdated: 5,
      })
    );
  });

  it("modification concurrente : une version périmée est refusée (conflit, pas d'écrasement)", async () => {
    const first = { currentQuantity: 900, version: 2, updatedBy: "ouvrierA", lastUpdated: 5 };
    await assertSucceeds(updateDoc(doc(as("ouvrierA"), "batches", "TAQ-2026-AAAAAAAAAA"), first));
    // Le second appareil partait aussi de la version 1
    await assertFails(
      updateDoc(doc(as("managerA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentQuantity: 800,
        version: 2,
        updatedBy: "managerA",
        lastUpdated: 6,
      })
    );
  });

  it("personne ne change l'organisation, la quantité initiale ou la conformité d'un lot", async () => {
    for (const change of [{ licenseNumber: "ORG-B" }, { initialQuantity: 5 }, { complianceStatus: "CONFORME" }]) {
      await assertFails(
        updateDoc(doc(as("adminA"), "batches", "TAQ-2026-AAAAAAAAAA"), { ...change, version: 2, updatedBy: "adminA", lastUpdated: 5 })
      );
    }
  });

  it("une mise à jour doit être attribuée à son auteur", async () => {
    await assertFails(
      updateDoc(doc(as("managerA"), "batches", "TAQ-2026-AAAAAAAAAA"), {
        currentQuantity: 950,
        version: 2,
        updatedBy: "quelqu-un-d-autre",
        lastUpdated: 5,
      })
    );
  });

  it("aucun lot ne peut être supprimé, même par l'admin", async () => {
    await assertFails(deleteDoc(doc(as("adminA"), "batches", "TAQ-2026-AAAAAAAAAA")));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("journal de traçabilité", () => {
  it("une action est attribuée à son auteur réel", async () => {
    await assertFails(
      setDoc(doc(as("ouvrierA"), "actions", "act-b"), actionDoc("act-b", "TAQ-2026-AAAAAAAAAA", "ORG-A", "managerA"))
    );
    await assertSucceeds(
      setDoc(doc(as("ouvrierA"), "actions", "act-b"), actionDoc("act-b", "TAQ-2026-AAAAAAAAAA", "ORG-A", "ouvrierA"))
    );
  });

  it("LECTEUR n'écrit pas dans le journal", async () => {
    await assertFails(
      setDoc(doc(as("lecteurA"), "actions", "act-c"), actionDoc("act-c", "TAQ-2026-AAAAAAAAAA", "ORG-A", "lecteurA"))
    );
  });

  it("une action ne peut être ni réécrite ni effacée, même par l'admin", async () => {
    await assertFails(updateDoc(doc(as("adminA"), "actions", "act-a"), { quantityAfter: 1000 }));
    await assertFails(deleteDoc(doc(as("adminA"), "actions", "act-a")));
  });

  it("rejouer la même action (synchronisation interrompue) ne crée pas de doublon et passe", async () => {
    const data = actionDoc("act-a", "TAQ-2026-AAAAAAAAAA", "ORG-A", "ouvrierA");
    await assertSucceeds(setDoc(doc(as("ouvrierA"), "actions", "act-a"), data));
  });

  it("un lot et sa première action s'écrivent ensemble", async () => {
    const db = as("managerA");
    const batch = writeBatch(db);
    batch.set(doc(db, "batches", "TAQ-2026-EEEEEEEEEE"), batchDoc("TAQ-2026-EEEEEEEEEE", "ORG-A", "managerA"));
    batch.set(doc(db, "actions", "act-e"), actionDoc("act-e", "TAQ-2026-EEEEEEEEEE", "ORG-A", "managerA"));
    await assertSucceeds(batch.commit());
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe("transferts", () => {
  it("l'expéditeur et le destinataire lisent le transfert, pas un tiers", async () => {
    await assertSucceeds(getDoc(doc(as("lecteurA"), "transfers", "trf-ab")));
    await assertSucceeds(getDoc(doc(as("lecteurB"), "transfers", "trf-ab")));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users", "tiers"), profile("tiers", { role: "ADMIN", licenseId: "ORG-C" }));
    });
    await assertFails(getDoc(doc(as("tiers"), "transfers", "trf-ab")));
  });

  it("seul un manager de l'organisation d'origine expédie", async () => {
    await assertFails(setDoc(doc(as("ouvrierA"), "transfers", "trf-2"), transferDoc("trf-2", "ORG-A", "ORG-B", "ouvrierA")));
    await assertFails(setDoc(doc(as("managerB"), "transfers", "trf-2"), transferDoc("trf-2", "ORG-A", "ORG-B", "managerB")));
    await assertSucceeds(setDoc(doc(as("managerA"), "transfers", "trf-2"), transferDoc("trf-2", "ORG-A", "ORG-B", "managerA")));
  });

  it("un transfert vers sa propre organisation est refusé", async () => {
    await assertFails(setDoc(doc(as("managerA"), "transfers", "trf-3"), transferDoc("trf-3", "ORG-A", "ORG-A", "managerA")));
  });

  it("le destinataire réceptionne une fois ; l'expéditeur ne peut pas réceptionner", async () => {
    const reception = { status: "RECEPTIONNE", receivedBy: "managerB", receivedAt: 9, batchSummaries: [] };
    await assertFails(updateDoc(doc(as("managerA"), "transfers", "trf-ab"), { ...reception, receivedBy: "managerA" }));
    await assertSucceeds(updateDoc(doc(as("managerB"), "transfers", "trf-ab"), reception));
    await assertFails(updateDoc(doc(as("managerB"), "transfers", "trf-ab"), { status: "REJETE", receivedBy: "managerB" }));
  });

  it("à la réception, le destinataire ne modifie ni l'origine ni les lots", async () => {
    await assertFails(
      updateDoc(doc(as("managerB"), "transfers", "trf-ab"), {
        status: "RECEPTIONNE",
        receivedBy: "managerB",
        receivedAt: 9,
        batchIds: ["TAQ-2026-ZZZZZZZZZZ"],
      })
    );
  });

  it("l'expéditeur annule tant que rien n'est reçu", async () => {
    await assertSucceeds(updateDoc(doc(as("managerA"), "transfers", "trf-ab"), { status: "ANNULE" }));
  });

  it("aucun transfert ne peut être supprimé", async () => {
    await assertFails(deleteDoc(doc(as("adminA"), "transfers", "trf-ab")));
  });
});
