package com.taqnid.batch.repository

import androidx.room.withTransaction
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.local.entity.TransferEntity
import com.taqnid.batch.data.model.*
import com.taqnid.batch.domain.BatchRules
import com.taqnid.batch.utils.TaqninIdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * Repository des manifestes de transfert.
 *
 * Expédier sort les lots du stock de l'expéditeur (action TRANSFERT_SORTANT)
 * dans la même transaction que la création du manifeste. Réceptionner crée,
 * chez le destinataire, un lot par lot reçu avec la quantité constatée, relié
 * au lot d'origine : chaque organisation ne tient que ses propres lots.
 */
class TransferRepository(
    private val db: AppDatabase,
    private val batchRepository: BatchRepository = BatchRepository(db),
) {
    private val transferDao = db.transferDao()
    private val batchDao = db.batchDao()

    companion object {
        const val MAX_BATCHES = 50
    }

    fun observeAll(): Flow<List<Transfer>> =
        transferDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeById(id: String): Flow<Transfer?> =
        transferDao.observeById(id).map { it?.toModel() }

    fun observeByStatus(status: TransferStatus): Flow<List<Transfer>> =
        transferDao.observeByStatus(status.name).map { list -> list.map { it.toModel() } }

    fun observeInTransit(): Flow<List<Transfer>> =
        observeByStatus(TransferStatus.EN_TRANSIT)

    suspend fun getByNumber(number: String): Transfer? =
        transferDao.getByNumber(number)?.toModel()

    suspend fun getTransfersInPeriod(from: Date, to: Date): List<Transfer> =
        transferDao.getTransfersInPeriod(from.time, to.time).map { it.toModel() }

    private fun requireManager(user: User) {
        if (!user.isActive || user.licenseId.isBlank()) {
            throw BusinessRuleException("Compte non rattaché à une organisation : transfert impossible")
        }
        if (!user.canTransfer()) throw BusinessRuleException("Seul un manager peut expédier ou réceptionner un transfert")
    }

    /**
     * Crée le manifeste et sort les lots du stock, en une seule transaction.
     * Les lots sont relus dans la transaction : un second appui sur
     * « Expédier » trouve des lots vides et est refusé, sans doublon.
     */
    suspend fun createTransfer(
        user: User,
        destinationLicenseId: String,
        destinationLicenseName: String,
        batchIds: List<String>,
        driverName: String,
        vehiclePlate: String,
        notes: String = "",
    ): Transfer {
        requireManager(user)
        val destination = destinationLicenseId.trim()
        if (destination.isEmpty()) throw BusinessRuleException("Licence du destinataire requise")
        if (destination == user.licenseId) throw BusinessRuleException("Le destinataire doit être une autre organisation")
        if (batchIds.isEmpty()) throw BusinessRuleException("Aucun lot sélectionné")
        if (batchIds.size > MAX_BATCHES) throw BusinessRuleException("$MAX_BATCHES lots au plus par manifeste")
        if (driverName.isBlank()) throw BusinessRuleException("Conducteur requis")

        return db.withTransaction {
            val batches = batchIds.distinct().map { id ->
                val batch = batchDao.getById(id)?.toModel() ?: throw BusinessRuleException("Lot introuvable")
                if (batch.licenseNumber != user.licenseId) {
                    throw BusinessRuleException("Le lot ${batch.taqninId} n'appartient pas à votre organisation")
                }
                val check = BatchRules.checkTransferable(batch.currentStage, batch.currentQuantity)
                if (check is BatchRules.Check.Refused) throw BusinessRuleException("${batch.taqninId} : ${check.message}")
                batch
            }
            val transferNumber = TaqninIdGenerator.generateTransferNumber()
            val now = Date()
            val transfer = Transfer(
                id = UUID.randomUUID().toString(),
                transferNumber = transferNumber,
                status = TransferStatus.EN_TRANSIT,
                originLicenseId = user.licenseId,
                originLicenseName = user.licenseName,
                destinationLicenseId = destination,
                destinationLicenseName = destinationLicenseName.trim(),
                batchIds = batches.map { it.taqninId },
                batchSummaries = batches.map { b ->
                    TransferBatchItem(
                        taqninId = b.taqninId,
                        variety = b.variety,
                        stage = b.currentStage,
                        quantity = b.currentQuantity,
                        unit = b.unit,
                    )
                },
                driverName = driverName.trim(),
                vehiclePlate = vehiclePlate.trim(),
                qrCodeContent = "TRF:$transferNumber|FROM:${user.licenseId}|TO:$destination|LOTS:" +
                    batches.joinToString(",") { it.taqninId },
                createdBy = user.uid,
                createdByName = user.displayName,
                createdAt = now,
                dispatchedAt = now,
                notes = notes.take(2000),
            )
            transferDao.insert(TransferEntity.fromModel(transfer))
            for (batch in batches) {
                batchRepository.applyQuantityChange(
                    user, batch, ActionType.TRANSFERT_SORTANT, 0.0,
                    "Expédié — transfert $transferNumber vers ${transfer.destinationLicenseName.ifBlank { destination }}"
                )
            }
            transfer
        }
    }

    /**
     * Réception par le destinataire, une seule fois, avec les quantités
     * constatées (jamais plus que l'expédié). Un lot est créé chez le
     * destinataire pour chaque quantité reçue non nulle.
     */
    suspend fun receiveTransfer(user: User, transferId: String, receivedQuantities: Map<String, Double>): Transfer {
        requireManager(user)
        return db.withTransaction {
            val transfer = transferDao.getById(transferId)?.toModel() ?: throw BusinessRuleException("Transfert introuvable")
            if (transfer.destinationLicenseId != user.licenseId) {
                throw BusinessRuleException("Seul le destinataire peut réceptionner ce transfert")
            }
            if (transfer.status != TransferStatus.EN_TRANSIT) {
                throw BusinessRuleException("Transfert déjà ${transfer.status.labelFr.lowercase()}")
            }
            val items = transfer.batchSummaries.map { item ->
                val received = receivedQuantities[item.taqninId] ?: item.quantity
                if (received.isNaN() || received < 0.0 || received > item.quantity) {
                    throw BusinessRuleException("${item.taqninId} : quantité reçue entre 0 et ${item.quantity} ${item.unit}")
                }
                item.copy(quantityReceived = received, discrepancy = received - item.quantity)
            }
            val updated = transfer.copy(
                status = TransferStatus.RECEPTIONNE,
                receivedAt = Date(),
                receivedBy = user.uid,
                batchSummaries = items,
                isSynced = false,
                syncState = SyncState.PENDING,
                syncMessage = null,
            )
            transferDao.update(TransferEntity.fromModel(updated))
            for (item in items) {
                val quantity = item.quantityReceived ?: 0.0
                if (quantity <= 0.0) continue
                batchRepository.createBatch(
                    user = user,
                    variety = item.variety,
                    type = BatchType.PRODUIT_FINI,
                    initialQuantity = quantity,
                    unit = item.unit,
                    locationId = "",
                    locationName = "Réception ${transfer.transferNumber}",
                    notes = "Reçu de ${transfer.originLicenseName} (${transfer.originLicenseId}), lot d'origine ${item.taqninId}",
                    parentTaqninId = item.taqninId,
                    creationLabel = "Réception du transfert ${transfer.transferNumber} : $quantity ${item.unit} " +
                        "(expédié ${item.quantity}, écart ${item.discrepancy})",
                )
            }
            updated
        }
    }

    /** Refus du chargement par le destinataire : rien n'entre dans son stock. */
    suspend fun rejectTransfer(user: User, transferId: String) {
        requireManager(user)
        db.withTransaction {
            val transfer = transferDao.getById(transferId)?.toModel() ?: throw BusinessRuleException("Transfert introuvable")
            if (transfer.destinationLicenseId != user.licenseId) throw BusinessRuleException("Seul le destinataire peut refuser ce transfert")
            if (transfer.status != TransferStatus.EN_TRANSIT) throw BusinessRuleException("Transfert déjà ${transfer.status.labelFr.lowercase()}")
            transferDao.update(
                TransferEntity.fromModel(
                    transfer.copy(
                        status = TransferStatus.REJETE,
                        receivedAt = Date(),
                        receivedBy = user.uid,
                        isSynced = false,
                        syncState = SyncState.PENDING,
                    )
                )
            )
        }
    }
}
