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

    @Query("SELECT * FROM batch_actions WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ActionEntity>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(actions: List<ActionEntity>)

    @Query("UPDATE batch_actions SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(action: ActionEntity)
}
