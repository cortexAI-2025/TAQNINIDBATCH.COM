package com.taqnid.batch.repository

import com.taqnid.batch.data.local.dao.TransferDao
import com.taqnid.batch.data.local.entity.TransferEntity
import com.taqnid.batch.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * Repository pour les manifestes de transfert.
 */
class TransferRepository(
    private val transferDao: TransferDao
) {

    fun observeAll(): Flow<List<Transfer>> =
        transferDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeById(id: String): Flow<Transfer?> =
        transferDao.observeById(id).map { it?.toModel() }

    fun observeByStatus(status: TransferStatus): Flow<List<Transfer>> =
        transferDao.observeByStatus(status.name).map { list -> list.map { it.toModel() } }

    fun observeInTransit(): Flow<List<Transfer>> =
        observeByStatus(TransferStatus.EN_TRANSIT)

    suspend fun getUnsyncedTransfers(): List<Transfer> =
        transferDao.getUnsynced().map { it.toModel() }

    suspend fun getTransfersInPeriod(from: Date, to: Date): List<Transfer> =
        transferDao.getTransfersInPeriod(from.time, to.time).map { it.toModel() }

    /**
     * Crée un nouveau manifeste de transfert et génère son QR code content.
     */
    suspend fun createTransfer(
        originLicenseId: String,
        originLicenseName: String,
        destinationLicenseId: String,
        destinationLicenseName: String,
        batches: List<Batch>,
        driverName: String,
        vehiclePlate: String,
        createdBy: String,
        createdByName: String,
        notes: String = ""
    ): Transfer {
        val id = UUID.randomUUID().toString()
        val transferNumber = generateTransferNumber()
        val batchItems = batches.map { b ->
            TransferBatchItem(
                taqninId = b.taqninId,
                variety = b.variety,
                stage = b.currentStage,
                quantity = b.currentQuantity,
                unit = b.unit
            )
        }
        // Contenu du QR de transport : format compact JSON-like
        val qrContent = buildString {
            append("TRF:")
            append(transferNumber)
            append("|FROM:")
            append(originLicenseId)
            append("|TO:")
            append(destinationLicenseId)
            append("|LOTS:")
            append(batches.joinToString(",") { it.taqninId })
        }
        val transfer = Transfer(
            id = id,
            transferNumber = transferNumber,
            status = TransferStatus.EN_TRANSIT,
            originLicenseId = originLicenseId,
            originLicenseName = originLicenseName,
            destinationLicenseId = destinationLicenseId,
            destinationLicenseName = destinationLicenseName,
            batchIds = batches.map { it.taqninId },
            batchSummaries = batchItems,
            driverName = driverName,
            vehiclePlate = vehiclePlate,
            qrCodeContent = qrContent,
            createdBy = createdBy,
            createdByName = createdByName,
            createdAt = Date(),
            dispatchedAt = Date(),
            notes = notes,
            isSynced = false
        )
        transferDao.insert(TransferEntity.fromModel(transfer))
        return transfer
    }

    suspend fun receiveTransfer(
        transfer: Transfer,
        receivedBy: String,
        receivedQuantities: Map<String, Double> // taqninId → quantité reçue
    ): Transfer {
        val updatedSummaries = transfer.batchSummaries.map { item ->
            val received = receivedQuantities[item.taqninId] ?: item.quantity
            item.copy(
                quantityReceived = received,
                discrepancy = received - item.quantity
            )
        }
        val updated = transfer.copy(
            status = TransferStatus.RECEPTIONNE,
            receivedAt = Date(),
            receivedBy = receivedBy,
            batchSummaries = updatedSummaries,
            isSynced = false
        )
        transferDao.update(TransferEntity.fromModel(updated))
        return updated
    }

    suspend fun updateStatus(id: String, status: TransferStatus) {
        transferDao.updateStatus(id, status.name)
    }

    suspend fun markTransferSynced(id: String) = transferDao.markSynced(id)

    private fun generateTransferNumber(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val seq = System.currentTimeMillis() % 100000
        return "TRF-$year-${seq.toString().padStart(5, '0')}"
    }
}
