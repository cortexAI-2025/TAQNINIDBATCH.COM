package com.taqnid.batch.data.local.dao

import androidx.room.*
import com.taqnid.batch.data.local.entity.TransferEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO Room pour les manifestes de transfert.
 */
@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TransferEntity?>

    @Query("SELECT * FROM transfers WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE originLicenseId = :licenseId OR destinationLicenseId = :licenseId ORDER BY createdAt DESC")
    fun observeByLicense(licenseId: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE isSynced = 0 AND syncState = 'PENDING'")
    suspend fun getUnsynced(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE transferNumber = :number LIMIT 1")
    suspend fun getByNumber(number: String): TransferEntity?

    @Query("SELECT COUNT(*) FROM transfers WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT COUNT(*) FROM transfers WHERE isSynced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transfers WHERE syncState = 'REJECTED'")
    fun observeRejectedCount(): Flow<Int>

    @Upsert
    suspend fun upsert(transfer: TransferEntity)

    @Query("""
        SELECT * FROM transfers
        WHERE createdAt >= :from AND createdAt <= :to
        ORDER BY createdAt ASC
    """)
    suspend fun getTransfersInPeriod(from: Long, to: Long): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("UPDATE transfers SET status = :status, isSynced = 0, syncState = 'PENDING' WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /** Marqué envoyé seulement si son statut n'a pas changé pendant l'envoi. */
    @Query("UPDATE transfers SET isSynced = 1, syncState = 'SYNCED', syncMessage = NULL WHERE id = :id AND status = :status")
    suspend fun markSyncedIfUnchanged(id: String, status: String): Int

    @Query("UPDATE transfers SET syncState = 'REJECTED', syncMessage = :message WHERE id = :id")
    suspend fun markRejected(id: String, message: String)
}
