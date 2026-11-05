package com.taqnid.batch.data.local.dao

import androidx.room.*
import com.taqnid.batch.data.local.entity.ActionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room pour les actions de traçabilité sur les lots.
 */
@Dao
interface ActionDao {

    @Query("SELECT * FROM batch_actions WHERE batchId = :batchId ORDER BY timestamp DESC")
    fun observeActionsForBatch(batchId: String): Flow<List<ActionEntity>>

    @Query("SELECT * FROM batch_actions WHERE batchId = :batchId ORDER BY timestamp ASC")
    suspend fun getActionsForBatch(batchId: String): List<ActionEntity>

    @Query("SELECT * FROM batch_actions WHERE isSynced = 0 AND syncState = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<ActionEntity>

    @Query("SELECT * FROM batch_actions WHERE batchId = :batchId AND isSynced = 0 AND syncState = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getUnsyncedForBatch(batchId: String): List<ActionEntity>

    @Query("SELECT COUNT(*) FROM batch_actions WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT COUNT(*) FROM batch_actions WHERE isSynced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM batch_actions WHERE syncState = 'REJECTED'")
    fun observeRejectedCount(): Flow<Int>

    /** Action reçue du serveur : déjà connue, on garde la ligne locale. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(action: ActionEntity)

    @Query("""
        SELECT * FROM batch_actions
        WHERE timestamp >= :from AND timestamp <= :to
        ORDER BY timestamp ASC
    """)
    suspend fun getActionsInPeriod(from: Long, to: Long): List<ActionEntity>

    @Query("""
        SELECT * FROM batch_actions
        WHERE actionType IN ('RETRAIT_VENTE','RETRAIT_TEST','RETRAIT_DESTRUCTION')
          AND timestamp >= :from AND timestamp <= :to
        ORDER BY timestamp ASC
    """)
    suspend fun getRetraitsInPeriod(from: Long, to: Long): List<ActionEntity>

    /** Le journal ne s'écrit qu'en ajout : une action existante n'est jamais remplacée. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(action: ActionEntity)

    @Query("UPDATE batch_actions SET isSynced = 1, syncState = 'SYNCED', syncMessage = NULL WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE batch_actions SET syncState = 'REJECTED', syncMessage = :message WHERE id = :id")
    suspend fun markRejected(id: String, message: String)
}
