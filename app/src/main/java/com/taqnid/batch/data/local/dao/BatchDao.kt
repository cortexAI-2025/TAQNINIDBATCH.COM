package com.taqnid.batch.data.local.dao

import androidx.room.*
import com.taqnid.batch.data.local.entity.BatchEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room pour les opérations CRUD sur les lots.
 */
@Dao
interface BatchDao {

    // ── Lectures ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM batches ORDER BY lastUpdated DESC")
    fun observeAll(): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE id = :id")
    fun observeById(id: String): Flow<BatchEntity?>

    @Query("SELECT * FROM batches WHERE taqninId = :taqninId LIMIT 1")
    suspend fun getByTaqninId(taqninId: String): BatchEntity?

    @Query("SELECT * FROM batches WHERE currentStage = :stage ORDER BY lastUpdated DESC")
    fun observeByStage(stage: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE locationId = :locationId ORDER BY lastUpdated DESC")
    fun observeByLocation(locationId: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE ownerId = :ownerId ORDER BY lastUpdated DESC")
    fun observeByOwner(ownerId: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batches WHERE isSynced = 0")
    suspend fun getUnsynced(): List<BatchEntity>

    @Query("SELECT * FROM batches ORDER BY lastUpdated DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<BatchEntity>>

    @Query("""
        SELECT * FROM batches
        WHERE variety LIKE '%' || :query || '%'
           OR taqninId LIKE '%' || :query || '%'
           OR ownerName LIKE '%' || :query || '%'
        ORDER BY lastUpdated DESC
    """)
    fun search(query: String): Flow<List<BatchEntity>>

    @Query("SELECT SUM(currentQuantity) FROM batches WHERE locationId = :locationId")
    suspend fun getTotalQuantityInLocation(locationId: String): Double?

    @Query("SELECT COUNT(*) FROM batches WHERE currentStage NOT IN ('VENTE','DETRUIT','RETIRE')")
    fun observeActiveBatchCount(): Flow<Int>

    // ── Alertes ───────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM batches
        WHERE expirationDate IS NOT NULL
          AND expirationDate <= :threshold
          AND currentStage NOT IN ('VENTE','DETRUIT','RETIRE')
    """)
    fun observeExpiringBatches(threshold: Long): Flow<List<BatchEntity>>

    @Query("""
        SELECT * FROM batches
        WHERE currentQuantity < :lowStockThreshold
          AND currentStage NOT IN ('VENTE','DETRUIT','RETIRE')
    """)
    fun observeLowStockBatches(lowStockThreshold: Double = 50.0): Flow<List<BatchEntity>>

    // ── Rapports ──────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM batches
        WHERE lastUpdated >= :from AND lastUpdated <= :to
        ORDER BY lastUpdated ASC
    """)
    suspend fun getBatchesInPeriod(from: Long, to: Long): List<BatchEntity>

    // ── Écritures ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: BatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<BatchEntity>)

    @Update
    suspend fun update(batch: BatchEntity)

    @Query("UPDATE batches SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE batches SET currentQuantity = :qty, lastUpdated = :ts WHERE id = :id")
    suspend fun updateQuantity(id: String, qty: Double, ts: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET currentStage = :stage, lastUpdated = :ts WHERE id = :id")
    suspend fun updateStage(id: String, stage: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE batches SET ownerId = :ownerId, ownerName = :ownerName, licenseNumber = :license, lastUpdated = :ts WHERE id = :id")
    suspend fun updateOwner(id: String, ownerId: String, ownerName: String, license: String, ts: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(batch: BatchEntity)

    @Query("DELETE FROM batches WHERE id = :id")
    suspend fun deleteById(id: String)
}
