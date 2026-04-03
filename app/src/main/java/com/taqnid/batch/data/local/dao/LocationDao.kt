package com.taqnid.batch.data.local.dao

import androidx.room.*
import com.taqnid.batch.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room pour les emplacements.
 */
@Dao
interface LocationDao {

    @Query("SELECT * FROM locations WHERE isActive = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE licenseId = :licenseId AND isActive = 1 ORDER BY name ASC")
    fun observeByLicense(licenseId: String): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)

    @Update
    suspend fun update(location: LocationEntity)

    @Query("UPDATE locations SET currentLoad = :load WHERE id = :id")
    suspend fun updateLoad(id: String, load: Double)

    @Delete
    suspend fun delete(location: LocationEntity)
}
