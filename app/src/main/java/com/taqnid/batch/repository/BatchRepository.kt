package com.taqnid.batch.repository

import com.taqnid.batch.data.local.dao.ActionDao
import com.taqnid.batch.data.local.dao.BatchDao
import com.taqnid.batch.data.local.entity.ActionEntity
import com.taqnid.batch.data.local.entity.BatchEntity
import com.taqnid.batch.data.model.*
import com.taqnid.batch.utils.TaqninIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * Repository principal — source unique de vérité pour les lots.
 * Combine les données Room (offline) et Firebase (cloud via SyncWorker).
 */
class BatchRepository(
    private val batchDao: BatchDao,
    private val actionDao: ActionDao
) {

    // ── Observations (Flow) ───────────────────────────────────────────────────

    fun observeAllBatches(): Flow<List<Batch>> =
        batchDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeBatchById(id: String): Flow<Batch?> =
        batchDao.observeById(id).map { it?.toModel() }

    fun observeRecentBatches(limit: Int = 10): Flow<List<Batch>> =
        batchDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    fun observeActiveBatchCount(): Flow<Int> =
        batchDao.observeActiveBatchCount()

    fun observeActionsForBatch(batchId: String): Flow<List<BatchAction>> =
        actionDao.observeActionsForBatch(batchId).map { list -> list.map { it.toModel() } }

    fun searchBatches(query: String): Flow<List<Batch>> =
        batchDao.search(query).map { list -> list.map { it.toModel() } }

    fun observeExpiringBatches(daysAhead: Int = 7): Flow<List<Batch>> {
        val threshold = System.currentTimeMillis() + daysAhead * 24 * 3600 * 1000L
        return batchDao.observeExpiringBatches(threshold).map { list -> list.map { it.toModel() } }
    }

    fun observeLowStockBatches(threshold: Double = 50.0): Flow<List<Batch>> =
        batchDao.observeLowStockBatches(threshold).map { list -> list.map { it.toModel() } }

    // ── Lecture unique ─────────────────────────────────────────────────────────

    suspend fun getBatchByTaqninId(taqninId: String): Batch? =
        batchDao.getByTaqninId(taqninId)?.toModel()

    suspend fun getUnsyncedBatches(): List<Batch> =
        batchDao.getUnsynced().map { it.toModel() }

    suspend fun getUnsyncedActions(): List<BatchAction> =
        actionDao.getUnsynced().map { it.toModel() }

    suspend fun getBatchesInPeriod(from: Date, to: Date): List<Batch> =
        batchDao.getBatchesInPeriod(from.time, to.time).map { it.toModel() }

    suspend fun getActionsInPeriod(from: Date, to: Date): List<BatchAction> =
        actionDao.getActionsInPeriod(from.time, to.time).map { it.toModel() }

    // ── Création d'un lot ──────────────────────────────────────────────────────

    suspend fun createBatch(
        variety: String,
        type: BatchType,
        initialQuantity: Double,
        unit: String,
        locationId: String,
        locationName: String,
        ownerId: String,
        ownerName: String,
        licenseNumber: String,
        notes: String = ""
    ): Batch {
        val taqninId = TaqninIdGenerator.generate()
        val now = Date()
        val batch = Batch(
            id = UUID.randomUUID().toString(),
            taqninId = taqninId,
            variety = variety,
            type = type,
            initialQuantity = initialQuantity,
            currentQuantity = initialQuantity,
            unit = unit,
            locationId = locationId,
            locationName = locationName,
            currentStage = BatchStage.GERMINATION,
            complianceStatus = ComplianceStatus.EN_ATTENTE,
            ownerId = ownerId,
            ownerName = ownerName,
            licenseNumber = licenseNumber,
            startDate = now,
            lastUpdated = now,
            notes = notes,
            isSynced = false
        )
        batchDao.insert(BatchEntity.fromModel(batch))

        // Enregistrer l'action initiale de création
        val creationAction = BatchAction(
            id = UUID.randomUUID().toString(),
            batchId = batch.id,
            taqninId = taqninId,
            actionType = ActionType.CHANGEMENT_STADE,
            performedBy = ownerId,
            performedByName = ownerName,
            timestamp = now,
            stageTo = BatchStage.GERMINATION,
            description = "Création du lot — quantité initiale : $initialQuantity $unit",
            isSynced = false
        )
        actionDao.insert(ActionEntity.fromModel(creationAction))

        return batch
    }

    // ── Changement de stade ────────────────────────────────────────────────────

    suspend fun changeStage(
        batch: Batch,
        newStage: BatchStage,
        performedBy: String,
        performedByName: String,
        description: String = ""
    ) {
        val now = Date()
        val action = BatchAction(
            id = UUID.randomUUID().toString(),
            batchId = batch.id,
            taqninId = batch.taqninId,
            actionType = ActionType.CHANGEMENT_STADE,
            performedBy = performedBy,
            performedByName = performedByName,
            timestamp = now,
            stageFrom = batch.currentStage,
            stageTo = newStage,
            description = description.ifEmpty { "Passage de ${batch.currentStage.labelFr} à ${newStage.labelFr}" },
            locationId = batch.locationId,
            isSynced = false
        )
        batchDao.updateStage(batch.id, newStage.name, now.time)
        actionDao.insert(ActionEntity.fromModel(action))
    }

    // ── Enregistrement d'une pesée / perte / ajout ─────────────────────────────

    suspend fun recordAction(
        batch: Batch,
        actionType: ActionType,
        newQuantity: Double,
        performedBy: String,
        performedByName: String,
        description: String
    ) {
        val now = Date()
        val delta = newQuantity - batch.currentQuantity
        val action = BatchAction(
            id = UUID.randomUUID().toString(),
            batchId = batch.id,
            taqninId = batch.taqninId,
            actionType = actionType,
            performedBy = performedBy,
            performedByName = performedByName,
            timestamp = now,
            quantityBefore = batch.currentQuantity,
            quantityAfter = newQuantity,
            quantityDelta = delta,
            unit = batch.unit,
            description = description,
            locationId = batch.locationId,
            isSynced = false
        )
        batchDao.updateQuantity(batch.id, newQuantity, now.time)
        actionDao.insert(ActionEntity.fromModel(action))
    }

    // ── Transfert de propriété ─────────────────────────────────────────────────

    suspend fun transferOwnership(
        batch: Batch,
        newOwnerId: String,
        newOwnerName: String,
        newLicense: String,
        performedBy: String,
        performedByName: String
    ) {
        val now = Date()
        batchDao.updateOwner(batch.id, newOwnerId, newOwnerName, newLicense, now.time)
        val action = BatchAction(
            id = UUID.randomUUID().toString(),
            batchId = batch.id,
            taqninId = batch.taqninId,
            actionType = ActionType.TRANSFERT_ENTRANT,
            performedBy = performedBy,
            performedByName = performedByName,
            timestamp = now,
            description = "Transfert de propriété vers $newOwnerName (licence : $newLicense)",
            locationId = batch.locationId,
            isSynced = false
        )
        actionDao.insert(ActionEntity.fromModel(action))
    }

    // ── Marquage synchronisation ───────────────────────────────────────────────

    suspend fun markBatchSynced(batchId: String) = batchDao.markSynced(batchId)
    suspend fun markActionSynced(actionId: String) = actionDao.markSynced(actionId)
}
