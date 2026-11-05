package com.taqnid.batch.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.taqnid.batch.data.model.*
import com.taqnid.batch.domain.SyncDecision
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Accès Firestore.
 *
 * Structure :
 *   /users/{uid}             profil ; rôle, organisation, activation : SDK Admin seulement
 *   /batches/{taqninId}      lot, champ licenseNumber = organisation, champ version
 *   /actions/{actionId}      journal, en ajout seulement, champ orgId = organisation
 *   /transfers/{transferId}  manifeste, lisible par l'expéditeur et le destinataire
 *
 * Chaque écriture est rejouable : identifiants fixés sur l'appareil, et les
 * règles acceptent une écriture identique à l'existant. Une réponse perdue
 * puis un nouvel envoi ne créent donc jamais de doublon.
 */
class FirebaseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        const val COL_BATCHES = "batches"
        const val COL_ACTIONS = "actions"
        const val COL_TRANSFERS = "transfers"
        const val COL_USERS = "users"
    }

    // ── Lots ──────────────────────────────────────────────────────────────────

    private fun batchMap(batch: Batch, version: Long): Map<String, Any?> = mapOf(
        "id" to batch.id,
        "taqninId" to batch.taqninId,
        "variety" to batch.variety,
        "type" to batch.type.name,
        "initialQuantity" to batch.initialQuantity,
        "currentQuantity" to batch.currentQuantity,
        "unit" to batch.unit,
        "locationId" to batch.locationId,
        "locationName" to batch.locationName,
        "currentStage" to batch.currentStage.name,
        "complianceStatus" to batch.complianceStatus.name,
        "ownerId" to batch.ownerId,
        "ownerName" to batch.ownerName,
        "licenseNumber" to batch.licenseNumber,
        "createdBy" to batch.createdBy,
        "updatedBy" to batch.updatedBy,
        "parentTaqninId" to batch.parentTaqninId,
        "version" to version,
        "startDate" to batch.startDate.time,
        "lastUpdated" to batch.lastUpdated.time,
        "expirationDate" to batch.expirationDate?.time,
        "notes" to batch.notes,
    )

    /** Seuls les champs qu'une mise à jour peut changer (règles Firestore). */
    private fun batchUpdateMap(batch: Batch, version: Long): Map<String, Any?> = mapOf(
        "currentQuantity" to batch.currentQuantity,
        "currentStage" to batch.currentStage.name,
        "locationId" to batch.locationId,
        "locationName" to batch.locationName,
        "notes" to batch.notes,
        "expirationDate" to batch.expirationDate?.time,
        "lastUpdated" to batch.lastUpdated.time,
        "updatedBy" to batch.updatedBy,
        "version" to version,
    )

    private fun sameContent(doc: DocumentSnapshot, batch: Batch): Boolean =
        doc.getDouble("currentQuantity") == batch.currentQuantity &&
            doc.getString("currentStage") == batch.currentStage.name &&
            doc.getString("locationName").orEmpty() == batch.locationName &&
            doc.getString("notes").orEmpty() == batch.notes &&
            doc.getLong("expirationDate") == batch.expirationDate?.time

    fun parseBatch(doc: DocumentSnapshot): Batch? {
        if (!doc.exists()) return null
        return runCatching {
            Batch(
                id = doc.getString("id") ?: return null,
                taqninId = doc.getString("taqninId") ?: doc.id,
                variety = doc.getString("variety") ?: "",
                type = BatchType.valueOf(doc.getString("type") ?: BatchType.GRAINE.name),
                initialQuantity = doc.getDouble("initialQuantity") ?: 0.0,
                currentQuantity = doc.getDouble("currentQuantity") ?: 0.0,
                unit = doc.getString("unit") ?: "g",
                locationId = doc.getString("locationId") ?: "",
                locationName = doc.getString("locationName") ?: "",
                currentStage = BatchStage.valueOf(doc.getString("currentStage") ?: BatchStage.GERMINATION.name),
                complianceStatus = ComplianceStatus.valueOf(doc.getString("complianceStatus") ?: ComplianceStatus.EN_ATTENTE.name),
                ownerId = doc.getString("ownerId") ?: "",
                ownerName = doc.getString("ownerName") ?: "",
                licenseNumber = doc.getString("licenseNumber") ?: "",
                startDate = Date(doc.getLong("startDate") ?: 0L),
                lastUpdated = Date(doc.getLong("lastUpdated") ?: 0L),
                expirationDate = doc.getLong("expirationDate")?.let { Date(it) },
                notes = doc.getString("notes") ?: "",
                isSynced = true,
                version = doc.getLong("version") ?: 1L,
                syncState = SyncState.SYNCED,
                createdBy = doc.getString("createdBy") ?: "",
                updatedBy = doc.getString("updatedBy") ?: "",
                parentTaqninId = doc.getString("parentTaqninId"),
            )
        }.getOrNull()
    }

    suspend fun downloadBatch(taqninId: String): Batch? =
        parseBatch(db.collection(COL_BATCHES).document(taqninId).get().await())

    /**
     * Envoie un lot modifié hors ligne, et, s'il est nouveau, ses premières
     * actions dans la même transaction. La décision (création, version
     * suivante, déjà appliqué, conflit) se prend sur l'état serveur lu dans
     * la transaction : deux appareils ne peuvent pas s'écraser.
     */
    suspend fun pushBatch(batch: Batch, pendingActions: List<BatchAction>): SyncDecision {
        val ref = db.collection(COL_BATCHES).document(batch.taqninId)
        return db.runTransaction { tx ->
            val remote = tx.get(ref)
            val decision = SyncDecision.decide(
                baseVersion = batch.version,
                serverVersion = if (remote.exists()) (remote.getLong("version") ?: 1L) else null,
                sameContent = remote.exists() && sameContent(remote, batch),
            )
            when (decision) {
                is SyncDecision.Create -> {
                    tx.set(ref, batchMap(batch, 1L))
                    pendingActions.forEach { tx.set(db.collection(COL_ACTIONS).document(it.id), actionMap(it)) }
                }
                is SyncDecision.Update -> tx.update(ref, batchUpdateMap(batch, decision.nextVersion))
                else -> Unit
            }
            decision
        }.await()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun actionMap(action: BatchAction): Map<String, Any?> = mapOf(
        "id" to action.id,
        "batchId" to action.batchId,
        "taqninId" to action.taqninId,
        "orgId" to action.orgId,
        "actionType" to action.actionType.name,
        "performedBy" to action.performedBy,
        "performedByName" to action.performedByName,
        "timestamp" to action.timestamp.time,
        "quantityBefore" to action.quantityBefore,
        "quantityAfter" to action.quantityAfter,
        "quantityDelta" to action.quantityDelta,
        "unit" to action.unit,
        "stageFrom" to action.stageFrom?.name,
        "stageTo" to action.stageTo?.name,
        "description" to action.description,
        "locationId" to action.locationId
    )

    /** Écriture idempotente : même identifiant, même contenu. */
    suspend fun uploadAction(action: BatchAction) {
        db.collection(COL_ACTIONS).document(action.id).set(actionMap(action)).await()
    }

    private fun parseAction(doc: DocumentSnapshot): BatchAction? = runCatching {
        BatchAction(
            id = doc.getString("id") ?: doc.id,
            batchId = doc.getString("batchId") ?: return null,
            taqninId = doc.getString("taqninId") ?: "",
            actionType = ActionType.valueOf(doc.getString("actionType") ?: ActionType.NOTE.name),
            performedBy = doc.getString("performedBy") ?: "",
            performedByName = doc.getString("performedByName") ?: "",
            timestamp = Date(doc.getLong("timestamp") ?: 0L),
            quantityBefore = doc.getDouble("quantityBefore"),
            quantityAfter = doc.getDouble("quantityAfter"),
            quantityDelta = doc.getDouble("quantityDelta"),
            unit = doc.getString("unit") ?: "g",
            stageFrom = doc.getString("stageFrom")?.let { BatchStage.valueOf(it) },
            stageTo = doc.getString("stageTo")?.let { BatchStage.valueOf(it) },
            description = doc.getString("description") ?: "",
            locationId = doc.getString("locationId") ?: "",
            orgId = doc.getString("orgId") ?: "",
            isSynced = true,
            syncState = SyncState.SYNCED,
        )
    }.getOrNull()

    // ── Transferts ────────────────────────────────────────────────────────────

    private fun itemsList(transfer: Transfer): List<Map<String, Any?>> = transfer.batchSummaries.map {
        mapOf(
            "taqninId" to it.taqninId,
            "variety" to it.variety,
            "stage" to it.stage.name,
            "quantity" to it.quantity,
            "unit" to it.unit,
            "quantityReceived" to it.quantityReceived,
            "discrepancy" to it.discrepancy,
        )
    }

    private fun transferMap(transfer: Transfer): Map<String, Any?> = mapOf(
        "id" to transfer.id,
        "transferNumber" to transfer.transferNumber,
        "status" to transfer.status.name,
        "originLicenseId" to transfer.originLicenseId,
        "originLicenseName" to transfer.originLicenseName,
        "destinationLicenseId" to transfer.destinationLicenseId,
        "destinationLicenseName" to transfer.destinationLicenseName,
        "batchIds" to transfer.batchIds,
        "batchSummaries" to itemsList(transfer),
        "driverName" to transfer.driverName,
        "vehiclePlate" to transfer.vehiclePlate,
        "qrCodeContent" to transfer.qrCodeContent,
        "createdBy" to transfer.createdBy,
        "createdByName" to transfer.createdByName,
        "createdAt" to transfer.createdAt.time,
        "dispatchedAt" to transfer.dispatchedAt?.time,
        "receivedAt" to transfer.receivedAt?.time,
        "receivedBy" to transfer.receivedBy,
        "notes" to transfer.notes
    )

    /**
     * Envoie un transfert : création par l'expéditeur, puis seulement son
     * changement de statut (réception, refus, annulation).
     */
    suspend fun pushTransfer(transfer: Transfer) {
        val ref = db.collection(COL_TRANSFERS).document(transfer.id)
        db.runTransaction { tx ->
            val remote = tx.get(ref)
            if (!remote.exists()) {
                tx.set(ref, transferMap(transfer))
            } else if (remote.getString("status") != transfer.status.name) {
                val change = mutableMapOf<String, Any?>("status" to transfer.status.name)
                if (transfer.status == TransferStatus.RECEPTIONNE || transfer.status == TransferStatus.REJETE) {
                    change["receivedBy"] = transfer.receivedBy
                    change["receivedAt"] = transfer.receivedAt?.time
                    change["batchSummaries"] = itemsList(transfer)
                }
                tx.update(ref, change)
            }
            Unit
        }.await()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTransfer(doc: DocumentSnapshot): Transfer? = runCatching {
        val items = (doc.get("batchSummaries") as? List<Map<String, Any?>>).orEmpty().map {
            TransferBatchItem(
                taqninId = it["taqninId"] as? String ?: "",
                variety = it["variety"] as? String ?: "",
                stage = runCatching { BatchStage.valueOf(it["stage"] as String) }.getOrDefault(BatchStage.EMBALLAGE),
                quantity = (it["quantity"] as? Number)?.toDouble() ?: 0.0,
                unit = it["unit"] as? String ?: "g",
                quantityReceived = (it["quantityReceived"] as? Number)?.toDouble(),
                discrepancy = (it["discrepancy"] as? Number)?.toDouble(),
            )
        }
        Transfer(
            id = doc.getString("id") ?: doc.id,
            transferNumber = doc.getString("transferNumber") ?: return null,
            status = TransferStatus.valueOf(doc.getString("status") ?: return null),
            originLicenseId = doc.getString("originLicenseId") ?: "",
            originLicenseName = doc.getString("originLicenseName") ?: "",
            destinationLicenseId = doc.getString("destinationLicenseId") ?: "",
            destinationLicenseName = doc.getString("destinationLicenseName") ?: "",
            batchIds = (doc.get("batchIds") as? List<String>).orEmpty(),
            batchSummaries = items,
            driverName = doc.getString("driverName") ?: "",
            vehiclePlate = doc.getString("vehiclePlate") ?: "",
            qrCodeContent = doc.getString("qrCodeContent") ?: "",
            createdBy = doc.getString("createdBy") ?: "",
            createdByName = doc.getString("createdByName") ?: "",
            createdAt = Date(doc.getLong("createdAt") ?: 0L),
            dispatchedAt = doc.getLong("dispatchedAt")?.let { Date(it) },
            receivedAt = doc.getLong("receivedAt")?.let { Date(it) },
            receivedBy = doc.getString("receivedBy") ?: "",
            notes = doc.getString("notes") ?: "",
            isSynced = true,
            syncState = SyncState.SYNCED,
        )
    }.getOrNull()

    // ── Récupération des données de l'organisation ────────────────────────────

    suspend fun fetchOrgBatches(org: String): List<Batch> =
        db.collection(COL_BATCHES).whereEqualTo("licenseNumber", org).get().await()
            .documents.mapNotNull { parseBatch(it) }

    suspend fun fetchOrgActionsSince(org: String, since: Long): List<BatchAction> =
        db.collection(COL_ACTIONS).whereEqualTo("orgId", org).whereGreaterThanOrEqualTo("timestamp", since)
            .get().await().documents.mapNotNull { parseAction(it) }

    suspend fun fetchOrgTransfers(org: String): List<Transfer> {
        val sent = db.collection(COL_TRANSFERS).whereEqualTo("originLicenseId", org).get().await().documents
        val received = db.collection(COL_TRANSFERS).whereEqualTo("destinationLicenseId", org).get().await().documents
        return (sent + received).mapNotNull { parseTransfer(it) }.distinctBy { it.id }
    }

    // ── Utilisateurs ──────────────────────────────────────────────────────────

    /** Profil en attente : les règles refusent tout autre rôle, organisation ou activation. */
    suspend fun createPendingProfile(user: User) {
        val data = mapOf(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to user.displayName,
            "role" to UserRole.LECTEUR.name,
            "licenseId" to "",
            "licenseName" to "",
            "isActive" to false,
            "biometricEnabled" to false,
            "createdAt" to user.createdAt,
            "lastLoginAt" to System.currentTimeMillis()
        )
        db.collection(COL_USERS).document(user.uid).set(data).await()
    }

    suspend fun touchLastLogin(uid: String) {
        runCatching {
            db.collection(COL_USERS).document(uid)
                .set(mapOf("lastLoginAt" to System.currentTimeMillis()), SetOptions.merge()).await()
        }
    }

    suspend fun getUser(uid: String): User? {
        val doc = db.collection(COL_USERS).document(uid).get().await()
        return if (doc.exists()) {
            User(
                uid = uid,
                email = doc.getString("email") ?: "",
                displayName = doc.getString("displayName") ?: "",
                role = runCatching { UserRole.valueOf(doc.getString("role") ?: "") }.getOrDefault(UserRole.LECTEUR),
                licenseId = doc.getString("licenseId") ?: "",
                licenseName = doc.getString("licenseName") ?: "",
                // Absent ou illisible : inactif, jamais l'inverse
                isActive = doc.getBoolean("isActive") ?: false,
                biometricEnabled = doc.getBoolean("biometricEnabled") ?: false,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                lastLoginAt = doc.getLong("lastLoginAt") ?: System.currentTimeMillis()
            )
        } else null
    }
}
