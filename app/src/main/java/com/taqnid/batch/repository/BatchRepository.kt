package com.taqnid.batch.repository

import androidx.room.withTransaction
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.local.entity.ActionEntity
import com.taqnid.batch.data.local.entity.BatchEntity
import com.taqnid.batch.data.model.*
import com.taqnid.batch.domain.BatchRules
import com.taqnid.batch.utils.TaqninIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/** Refus d'une saisie, avec un message lisible par l'utilisateur. */
class BusinessRuleException(message: String) : Exception(message)

/**
 * Repository des lots — source de vérité locale (Room), envoyée au serveur
 * par SyncWorker.
 *
 * Chaque écriture modifie le lot ET ajoute son action au journal dans une
 * seule transaction : une interruption (appli tuée, batterie) ne laisse
 * jamais un lot modifié sans la trace de la modification, ni l'inverse.
 * Chaque saisie vérifie d'abord les règles métier (BatchRules) et les droits
 * du rôle ; les règles Firestore refont ces contrôles côté serveur.
 */
class BatchRepository(private val db: AppDatabase) {

    private val batchDao = db.batchDao()
    private val actionDao = db.actionDao()

    // ── Observations (Flow) ───────────────────────────────────────────────────

    fun observeAllBatches(): Flow<List<Batch>> =
        batchDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeBatchById(id: String): Flow<Batch?> =
        batchDao.observeById(id).map { it?.toModel() }

    fun observeRecentBatches(limit: Int = 10): Flow<List<Batch>> =
        batchDao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    fun observeActiveBatchCount(): Flow<Int> =
        batchDao.observeActiveBatchCount()

    fun observeTransferableBatches(): Flow<List<Batch>> =
        batchDao.observeTransferable().map { list -> list.map { it.toModel() } }

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

    suspend fun getBatchesInPeriod(from: Date, to: Date): List<Batch> =
        batchDao.getBatchesInPeriod(from.time, to.time).map { it.toModel() }

    suspend fun getActionsInPeriod(from: Date, to: Date): List<BatchAction> =
        actionDao.getActionsInPeriod(from.time, to.time).map { it.toModel() }

    // ── Contrôles ──────────────────────────────────────────────────────────────

    private fun require(check: BatchRules.Check) {
        if (check is BatchRules.Check.Refused) throw BusinessRuleException(check.message)
    }

    private fun requireActiveMember(user: User) {
        if (!user.isActive || user.licenseId.isBlank()) {
            throw BusinessRuleException("Compte non rattaché à une organisation : saisie impossible")
        }
    }

    // ── Création d'un lot ──────────────────────────────────────────────────────

    suspend fun createBatch(
        user: User,
        variety: String,
        type: BatchType,
        initialQuantity: Double?,
        unit: String,
        locationId: String,
        locationName: String,
        notes: String = "",
        parentTaqninId: String? = null,
        creationLabel: String? = null,
    ): Batch {
        requireActiveMember(user)
        if (!user.canCreate()) throw BusinessRuleException("Seul un manager peut créer un lot")
        require(BatchRules.checkUnit(unit))
        require(BatchRules.checkInitialQuantity(initialQuantity))
        if (variety.isBlank() || variety.length > 120) throw BusinessRuleException("Variété requise (120 caractères au plus)")
        val quantity = initialQuantity!!

        val now = Date()
        val batch = Batch(
            id = UUID.randomUUID().toString(),
            taqninId = TaqninIdGenerator.generate(),
            variety = variety.trim(),
            type = type,
            initialQuantity = quantity,
            currentQuantity = quantity,
            unit = unit,
            locationId = locationId,
            locationName = locationName,
            currentStage = BatchStage.GERMINATION,
            complianceStatus = ComplianceStatus.EN_ATTENTE,
            ownerId = user.uid,
            ownerName = user.displayName,
            // Le lot appartient à l'organisation du compte, jamais à une saisie libre
            licenseNumber = user.licenseId,
            startDate = now,
            lastUpdated = now,
            notes = notes.take(2000),
            createdBy = user.uid,
            updatedBy = user.uid,
            parentTaqninId = parentTaqninId,
        )
        val creationAction = BatchAction(
            id = UUID.randomUUID().toString(),
            batchId = batch.id,
            taqninId = batch.taqninId,
            actionType = if (parentTaqninId != null) ActionType.TRANSFERT_ENTRANT else ActionType.CHANGEMENT_STADE,
            performedBy = user.uid,
            performedByName = user.displayName,
            timestamp = now,
            quantityAfter = quantity,
            quantityDelta = quantity,
            unit = unit,
            stageTo = BatchStage.GERMINATION,
            description = creationLabel ?: "Création du lot — quantité initiale : $quantity $unit",
            orgId = user.licenseId,
        )
        db.withTransaction {
            batchDao.insert(BatchEntity.fromModel(batch))
            actionDao.insert(ActionEntity.fromModel(creationAction))
        }
        return batch
    }

    // ── Changement de stade ────────────────────────────────────────────────────

    suspend fun changeStage(user: User, batchId: String, newStage: BatchStage, description: String = "") {
        requireActiveMember(user)
        if (!user.canCreate()) throw BusinessRuleException("Seul un manager peut changer le stade d'un lot")
        db.withTransaction {
            // Relire dans la transaction : l'écran peut afficher un état périmé
            val batch = batchDao.getById(batchId)?.toModel() ?: throw BusinessRuleException("Lot introuvable")
            if (!BatchRules.canMove(batch.currentStage, newStage)) {
                throw BusinessRuleException("Passage de « ${batch.currentStage.labelFr} » à « ${newStage.labelFr} » non permis")
            }
            val now = Date()
            batchDao.updateStage(batch.id, newStage.name, now.time, user.uid)
            actionDao.insert(
                ActionEntity.fromModel(
                    BatchAction(
                        id = UUID.randomUUID().toString(),
                        batchId = batch.id,
                        taqninId = batch.taqninId,
                        actionType = ActionType.CHANGEMENT_STADE,
                        performedBy = user.uid,
                        performedByName = user.displayName,
                        timestamp = now,
                        stageFrom = batch.currentStage,
                        stageTo = newStage,
                        unit = batch.unit,
                        description = description.ifEmpty { "Passage de ${batch.currentStage.labelFr} à ${newStage.labelFr}" }.take(2000),
                        locationId = batch.locationId,
                        orgId = batch.licenseNumber,
                    )
                )
            )
        }
    }

    // ── Pesée, perte, retrait… ────────────────────────────────────────────────

    suspend fun recordAction(
        user: User,
        batchId: String,
        actionType: ActionType,
        newQuantity: Double?,
        description: String,
    ) {
        requireActiveMember(user)
        if (!user.canScan()) throw BusinessRuleException("Un lecteur ne peut pas enregistrer d'action")
        db.withTransaction {
            val batch = batchDao.getById(batchId)?.toModel() ?: throw BusinessRuleException("Lot introuvable")
            applyQuantityChange(user, batch, actionType, newQuantity, description)
        }
    }

    /** À appeler dans une transaction ouverte. */
    internal suspend fun applyQuantityChange(
        user: User,
        batch: Batch,
        actionType: ActionType,
        newQuantity: Double?,
        description: String,
    ) {
        if (BatchRules.isTerminal(batch.currentStage)) {
            throw BusinessRuleException("Lot ${batch.currentStage.labelFr.lowercase()} : plus aucune action possible")
        }
        // Une note n'a pas de quantité : la quantité reste celle du lot
        val quantity = if (actionType == ActionType.NOTE) batch.currentQuantity else newQuantity
        require(BatchRules.checkNewQuantity(batch.currentQuantity, quantity, actionType))
        val now = Date()
        if (quantity != batch.currentQuantity) {
            batchDao.updateQuantity(batch.id, quantity!!, now.time, user.uid)
        }
        actionDao.insert(
            ActionEntity.fromModel(
                BatchAction(
                    id = UUID.randomUUID().toString(),
                    batchId = batch.id,
                    taqninId = batch.taqninId,
                    actionType = actionType,
                    performedBy = user.uid,
                    performedByName = user.displayName,
                    timestamp = now,
                    quantityBefore = batch.currentQuantity,
                    quantityAfter = quantity,
                    quantityDelta = quantity!! - batch.currentQuantity,
                    unit = batch.unit,
                    description = description.take(2000),
                    locationId = batch.locationId,
                    orgId = batch.licenseNumber,
                )
            )
        )
    }

    // ── Conflits ──────────────────────────────────────────────────────────────

    /** Garder la saisie locale : elle sera réécrite par-dessus la version serveur connue. */
    suspend fun keepLocalVersion(batchId: String, serverVersion: Long) =
        batchDao.rebaseOnServer(batchId, serverVersion)

    /** Garder la version du serveur : le journal local reste, l'état du lot est remplacé. */
    suspend fun keepServerVersion(server: Batch) =
        batchDao.upsert(
            BatchEntity.fromModel(server.copy(isSynced = true, syncState = SyncState.SYNCED, syncMessage = null))
        )
}
