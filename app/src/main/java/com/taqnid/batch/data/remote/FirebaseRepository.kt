package com.taqnid.batch.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.taqnid.batch.data.model.*
import kotlinx.coroutines.tasks.await

/**
 * Dépôt Firebase Firestore pour la synchronisation cloud des données.
 *
 * Structure Firestore :
 *   /batches/{taqninId}
 *   /actions/{actionId}
 *   /transfers/{transferId}
 *   /locations/{locationId}
 *   /users/{uid}
 */
class FirebaseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val COL_BATCHES = "batches"
        private const val COL_ACTIONS = "actions"
        private const val COL_TRANSFERS = "transfers"
        private const val COL_LOCATIONS = "locations"
        private const val COL_USERS = "users"
    }

    // ── Lots ──────────────────────────────────────────────────────────────────

    suspend fun uploadBatch(batch: Batch) {
        val data = mapOf(
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
            "startDate" to batch.startDate.time,
            "lastUpdated" to batch.lastUpdated.time,
            "expirationDate" to batch.expirationDate?.time,
            "notes" to batch.notes
        )
        db.collection(COL_BATCHES)
            .document(batch.taqninId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun downloadBatch(taqninId: String): Batch? {
        val doc = db.collection(COL_BATCHES).document(taqninId).get().await()
        return if (doc.exists()) {
            Batch(
                id = doc.getString("id") ?: "",
                taqninId = doc.getString("taqninId") ?: taqninId,
                variety = doc.getString("variety") ?: "",
                type = BatchType.valueOf(doc.getString("type") ?: BatchType.GRAINE.name),
                initialQuantity = (doc.getDouble("initialQuantity") ?: 0.0),
                currentQuantity = (doc.getDouble("currentQuantity") ?: 0.0),
                unit = doc.getString("unit") ?: "g",
                locationId = doc.getString("locationId") ?: "",
                locationName = doc.getString("locationName") ?: "",
                currentStage = BatchStage.valueOf(doc.getString("currentStage") ?: BatchStage.GERMINATION.name),
                complianceStatus = ComplianceStatus.valueOf(doc.getString("complianceStatus") ?: ComplianceStatus.EN_ATTENTE.name),
                ownerId = doc.getString("ownerId") ?: "",
                ownerName = doc.getString("ownerName") ?: "",
                licenseNumber = doc.getString("licenseNumber") ?: "",
                startDate = java.util.Date(doc.getLong("startDate") ?: System.currentTimeMillis()),
                lastUpdated = java.util.Date(doc.getLong("lastUpdated") ?: System.currentTimeMillis()),
                expirationDate = doc.getLong("expirationDate")?.let { java.util.Date(it) },
                notes = doc.getString("notes") ?: "",
                isSynced = true
            )
        } else null
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    suspend fun uploadAction(action: BatchAction) {
        val data = mapOf(
            "id" to action.id,
            "batchId" to action.batchId,
            "taqninId" to action.taqninId,
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
        db.collection(COL_ACTIONS)
            .document(action.id)
            .set(data, SetOptions.merge())
            .await()
    }

    // ── Transferts ────────────────────────────────────────────────────────────

    suspend fun uploadTransfer(transfer: Transfer) {
        val data = mapOf(
            "id" to transfer.id,
            "transferNumber" to transfer.transferNumber,
            "status" to transfer.status.name,
            "originLicenseId" to transfer.originLicenseId,
            "originLicenseName" to transfer.originLicenseName,
            "destinationLicenseId" to transfer.destinationLicenseId,
            "destinationLicenseName" to transfer.destinationLicenseName,
            "batchIds" to transfer.batchIds,
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
        db.collection(COL_TRANSFERS)
            .document(transfer.id)
            .set(data, SetOptions.merge())
            .await()
    }

    // ── Utilisateurs ──────────────────────────────────────────────────────────

    suspend fun uploadUser(user: User) {
        val data = mapOf(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to user.displayName,
            "role" to user.role.name,
            "licenseId" to user.licenseId,
            "licenseName" to user.licenseName,
            "isActive" to user.isActive,
            "biometricEnabled" to user.biometricEnabled,
            "createdAt" to user.createdAt,
            "lastLoginAt" to System.currentTimeMillis()
        )
        db.collection(COL_USERS)
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun getUser(uid: String): User? {
        val doc = db.collection(COL_USERS).document(uid).get().await()
        return if (doc.exists()) {
            User(
                uid = uid,
                email = doc.getString("email") ?: "",
                displayName = doc.getString("displayName") ?: "",
                role = UserRole.valueOf(doc.getString("role") ?: UserRole.LECTEUR.name),
                licenseId = doc.getString("licenseId") ?: "",
                licenseName = doc.getString("licenseName") ?: "",
                isActive = doc.getBoolean("isActive") ?: true,
                biometricEnabled = doc.getBoolean("biometricEnabled") ?: false,
                createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                lastLoginAt = doc.getLong("lastLoginAt") ?: System.currentTimeMillis()
            )
        } else null
    }
}
