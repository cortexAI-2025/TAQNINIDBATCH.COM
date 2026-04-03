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

    @Query("SELECT * FROM transfers WHERE isSynced = 0")
    suspend fun getUnsynced(): List<TransferEntity>

    @Query("""
        SELECT * FROM transfers
        WHERE createdAt >= :from AND createdAt <= :to
        ORDER BY createdAt ASC
    """)
    suspend fun getTransfersInPeriod(from: Long, to: Long): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("UPDATE transfers SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE transfers SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Delete
    suspend fun delete(transfer: TransferEntity)
}
