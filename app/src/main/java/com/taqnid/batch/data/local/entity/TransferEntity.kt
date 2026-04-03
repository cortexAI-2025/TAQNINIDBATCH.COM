package com.taqnid.batch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.taqnid.batch.data.model.Transfer
import com.taqnid.batch.data.model.TransferBatchItem
import com.taqnid.batch.data.model.TransferStatus

/**
 * Entité Room représentant un manifeste de transfert.
 * La liste des lots est sérialisée en JSON (champ batchIdsJson).
 */
@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["transferNumber"], unique = true),
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val transferNumber: String,
    val status: String,                // TransferStatus.name
    val originLicenseId: String,
    val originLicenseName: String,
    val destinationLicenseId: String,
    val destinationLicenseName: String,
    val batchIdsJson: String,          // JSON: List<String>
    val batchSummariesJson: String,    // JSON: List<TransferBatchItem>
    val driverName: String,
    val vehiclePlate: String,
    val qrCodeContent: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Long,
    val dispatchedAt: Long?,
    val receivedAt: Long?,
    val receivedBy: String,
    val notes: String,
    val isSynced: Boolean = false
) {
    fun toModel(): Transfer {
        val gson = Gson()
        val batchIds: List<String> = gson.fromJson(batchIdsJson,
            object : TypeToken<List<String>>() {}.type)
        val batchSummaries: List<TransferBatchItem> = gson.fromJson(batchSummariesJson,
            object : TypeToken<List<TransferBatchItem>>() {}.type)
        return Transfer(
            id = id,
            transferNumber = transferNumber,
            status = TransferStatus.valueOf(status),
            originLicenseId = originLicenseId,
            originLicenseName = originLicenseName,
            destinationLicenseId = destinationLicenseId,
            destinationLicenseName = destinationLicenseName,
            batchIds = batchIds,
            batchSummaries = batchSummaries,
            driverName = driverName,
            vehiclePlate = vehiclePlate,
            qrCodeContent = qrCodeContent,
            createdBy = createdBy,
            createdByName = createdByName,
            createdAt = java.util.Date(createdAt),
            dispatchedAt = dispatchedAt?.let { java.util.Date(it) },
            receivedAt = receivedAt?.let { java.util.Date(it) },
            receivedBy = receivedBy,
            notes = notes,
            isSynced = isSynced
        )
    }

    companion object {
        fun fromModel(transfer: Transfer): TransferEntity {
            val gson = Gson()
            return TransferEntity(
                id = transfer.id,
                transferNumber = transfer.transferNumber,
                status = transfer.status.name,
                originLicenseId = transfer.originLicenseId,
                originLicenseName = transfer.originLicenseName,
                destinationLicenseId = transfer.destinationLicenseId,
                destinationLicenseName = transfer.destinationLicenseName,
                batchIdsJson = gson.toJson(transfer.batchIds),
                batchSummariesJson = gson.toJson(transfer.batchSummaries),
                driverName = transfer.driverName,
                vehiclePlate = transfer.vehiclePlate,
                qrCodeContent = transfer.qrCodeContent,
                createdBy = transfer.createdBy,
                createdByName = transfer.createdByName,
                createdAt = transfer.createdAt.time,
                dispatchedAt = transfer.dispatchedAt?.time,
                receivedAt = transfer.receivedAt?.time,
                receivedBy = transfer.receivedBy,
                notes = transfer.notes,
                isSynced = transfer.isSynced
            )
        }
    }
}
