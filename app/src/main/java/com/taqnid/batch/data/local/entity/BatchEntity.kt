package com.taqnid.batch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.taqnid.batch.data.model.*

/**
 * Entité Room représentant un lot en base locale (SQLite).
 */
@Entity(
    tableName = "batches",
    indices = [
        Index(value = ["taqninId"], unique = true),
        Index(value = ["currentStage"]),
        Index(value = ["ownerId"]),
        Index(value = ["lastUpdated"])
    ]
)
data class BatchEntity(
    @PrimaryKey val id: String,
    val taqninId: String,
    val variety: String,
    val type: String,                  // BatchType.name
    val initialQuantity: Double,
    val currentQuantity: Double,
    val unit: String,
    val locationId: String,
    val locationName: String,
    val currentStage: String,          // BatchStage.name
    val complianceStatus: String,      // ComplianceStatus.name
    val ownerId: String,
    val ownerName: String,
    val licenseNumber: String,
    val startDate: Long,               // Timestamp ms
    val lastUpdated: Long,
    val expirationDate: Long?,
    val notes: String,
    val isSynced: Boolean = false
) {
    fun toModel() = com.taqnid.batch.data.model.Batch(
        id = id,
        taqninId = taqninId,
        variety = variety,
        type = BatchType.valueOf(type),
        initialQuantity = initialQuantity,
        currentQuantity = currentQuantity,
        unit = unit,
        locationId = locationId,
        locationName = locationName,
        currentStage = BatchStage.valueOf(currentStage),
        complianceStatus = ComplianceStatus.valueOf(complianceStatus),
        ownerId = ownerId,
        ownerName = ownerName,
        licenseNumber = licenseNumber,
        startDate = java.util.Date(startDate),
        lastUpdated = java.util.Date(lastUpdated),
        expirationDate = expirationDate?.let { java.util.Date(it) },
        notes = notes,
        isSynced = isSynced
    )

    companion object {
        fun fromModel(batch: Batch) = BatchEntity(
            id = batch.id,
            taqninId = batch.taqninId,
            variety = batch.variety,
            type = batch.type.name,
            initialQuantity = batch.initialQuantity,
            currentQuantity = batch.currentQuantity,
            unit = batch.unit,
            locationId = batch.locationId,
            locationName = batch.locationName,
            currentStage = batch.currentStage.name,
            complianceStatus = batch.complianceStatus.name,
            ownerId = batch.ownerId,
            ownerName = batch.ownerName,
            licenseNumber = batch.licenseNumber,
            startDate = batch.startDate.time,
            lastUpdated = batch.lastUpdated.time,
            expirationDate = batch.expirationDate?.time,
            notes = batch.notes,
            isSynced = batch.isSynced
        )
    }
}
