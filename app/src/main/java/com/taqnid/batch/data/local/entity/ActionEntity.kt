package com.taqnid.batch.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.taqnid.batch.data.model.ActionType
import com.taqnid.batch.data.model.BatchAction
import com.taqnid.batch.data.model.BatchStage

/**
 * Entité Room représentant une action/événement sur un lot.
 */
@Entity(
    tableName = "batch_actions",
    foreignKeys = [
        ForeignKey(
            entity = BatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["timestamp"]),
        Index(value = ["actionType"])
    ]
)
data class ActionEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val taqninId: String,
    val actionType: String,            // ActionType.name
    val performedBy: String,
    val performedByName: String,
    val timestamp: Long,
    val quantityBefore: Double?,
    val quantityAfter: Double?,
    val quantityDelta: Double?,
    val unit: String,
    val stageFrom: String?,            // BatchStage.name
    val stageTo: String?,
    val description: String,
    val locationId: String,
    val isSynced: Boolean = false
) {
    fun toModel() = BatchAction(
        id = id,
        batchId = batchId,
        taqninId = taqninId,
        actionType = ActionType.valueOf(actionType),
        performedBy = performedBy,
        performedByName = performedByName,
        timestamp = java.util.Date(timestamp),
        quantityBefore = quantityBefore,
        quantityAfter = quantityAfter,
        quantityDelta = quantityDelta,
        unit = unit,
        stageFrom = stageFrom?.let { BatchStage.valueOf(it) },
        stageTo = stageTo?.let { BatchStage.valueOf(it) },
        description = description,
        locationId = locationId,
        isSynced = isSynced
    )

    companion object {
        fun fromModel(action: BatchAction) = ActionEntity(
            id = action.id,
            batchId = action.batchId,
            taqninId = action.taqninId,
            actionType = action.actionType.name,
            performedBy = action.performedBy,
            performedByName = action.performedByName,
            timestamp = action.timestamp.time,
            quantityBefore = action.quantityBefore,
            quantityAfter = action.quantityAfter,
            quantityDelta = action.quantityDelta,
            unit = action.unit,
            stageFrom = action.stageFrom?.name,
            stageTo = action.stageTo?.name,
            description = action.description,
            locationId = action.locationId,
            isSynced = action.isSynced
        )
    }
}
