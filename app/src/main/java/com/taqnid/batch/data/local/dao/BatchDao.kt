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

    @Query("SELECT * FROM batches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BatchEntity?

    /** Lots à envoyer : modifiés localement, ni en conflit ni refusés. */
    @Query("SELECT * FROM batches WHERE isSynced = 0 AND syncState = 'PENDING'")
    suspend fun getUnsynced(): List<BatchEntity>

    @Query("SELECT COUNT(*) FROM batches WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT COUNT(*) FROM batches WHERE isSynced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM batches WHERE syncState IN ('CONFLICT','REJECTED')")
    fun observeProblemCount(): Flow<Int>

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

    @Query("SELECT * FROM batches WHERE currentStage NOT IN ('VENTE','DETRUIT','RETIRE') AND currentQuantity > 0 ORDER BY lastUpdated DESC")
    fun observeTransferable(): Flow<List<BatchEntity>>

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

    /**
     * Mise à jour ou insertion SANS suppression préalable : REPLACE supprime
     * la ligne avant de la réinsérer, ce qui efface en cascade le journal
     * d'actions du lot.
     */
    @Upsert
    suspend fun upsert(batch: BatchEntity)

    @Update
    suspend fun update(batch: BatchEntity)

    /**
     * Marque le lot synchronisé à la version serveur donnée, seulement s'il
     * n'a pas été modifié depuis sa lecture : une saisie faite pendant
     * l'envoi reste en attente au lieu d'être perdue.
     * @return 1 si le lot a été marqué, 0 s'il a changé entre-temps
     */
    @Query("""
        UPDATE batches SET isSynced = 1, syncState = 'SYNCED', syncMessage = NULL, version = :version
        WHERE id = :id AND lastUpdated = :lastUpdated
    """)
    suspend fun markSyncedIfUnchanged(id: String, lastUpdated: Long, version: Long): Int

    /** Une saisie faite pendant l'envoi : garder la nouvelle version serveur comme base. */
    @Query("UPDATE batches SET version = :version WHERE id = :id")
    suspend fun setBaseVersion(id: String, version: Long)

    @Query("UPDATE batches SET syncState = 'CONFLICT', syncMessage = :message WHERE id = :id")
    suspend fun markConflict(id: String, message: String)

    @Query("UPDATE batches SET syncState = 'REJECTED', syncMessage = :message WHERE id = :id")
    suspend fun markRejected(id: String, message: String)

    /** Après un conflit, l'utilisateur garde sa saisie : elle repart de la version serveur. */
    @Query("UPDATE batches SET version = :serverVersion, syncState = 'PENDING', syncMessage = NULL, isSynced = 0 WHERE id = :id")
    suspend fun rebaseOnServer(id: String, serverVersion: Long)

    @Query("""
        UPDATE batches SET currentQuantity = :qty, lastUpdated = :ts, updatedBy = :by,
            isSynced = 0, syncState = 'PENDING', syncMessage = NULL
        WHERE id = :id
    """)
    suspend fun updateQuantity(id: String, qty: Double, ts: Long, by: String)

    @Query("""
        UPDATE batches SET currentStage = :stage, lastUpdated = :ts, updatedBy = :by,
            isSynced = 0, syncState = 'PENDING', syncMessage = NULL
        WHERE id = :id
    """)
    suspend fun updateStage(id: String, stage: String, ts: Long, by: String)
}
