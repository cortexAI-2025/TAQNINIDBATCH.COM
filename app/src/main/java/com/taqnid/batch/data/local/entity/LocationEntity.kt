package com.taqnid.batch.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.taqnid.batch.data.model.Location
import com.taqnid.batch.data.model.LocationType

/**
 * Entité Room représentant un emplacement physique.
 */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["licenseId"])]
)
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,          // LocationType.name
    val licenseId: String,
    val licenseName: String,
    val capacity: Double?,
    val currentLoad: Double,
    val isActive: Boolean,
    val description: String
) {
    fun toModel() = Location(
        id = id,
        name = name,
        type = LocationType.valueOf(type),
        licenseId = licenseId,
        licenseName = licenseName,
        capacity = capacity,
        currentLoad = currentLoad,
        isActive = isActive,
        description = description
    )

    companion object {
        fun fromModel(loc: Location) = LocationEntity(
            id = loc.id,
            name = loc.name,
            type = loc.type.name,
            licenseId = loc.licenseId,
            licenseName = loc.licenseName,
            capacity = loc.capacity,
            currentLoad = loc.currentLoad,
            isActive = loc.isActive,
            description = loc.description
        )
    }
}
