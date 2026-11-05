package com.taqnid.batch.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.taqnid.batch.data.local.dao.*
import com.taqnid.batch.data.local.entity.*

/**
 * Base de données Room principale.
 *
 * Version 2 : version serveur et état de synchronisation des lots, organisation
 * des actions, état de synchronisation des actions et des transferts.
 *
 * Aucune migration destructive : la base contient des saisies de terrain pas
 * encore envoyées, qu'une recréation effacerait sans prévenir. Une montée de
 * version sans migration fait échouer l'ouverture plutôt que de perdre des
 * données (et le test de migration le détecte avant publication).
 */
@Database(
    entities = [
        BatchEntity::class,
        ActionEntity::class,
        TransferEntity::class,
        LocationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun batchDao(): BatchDao
    abstract fun actionDao(): ActionDao
    abstract fun transferDao(): TransferDao
    abstract fun locationDao(): LocationDao

    /** Saisies (lots, actions, transferts) pas encore confirmées par le serveur. */
    suspend fun pendingCount(): Int =
        batchDao().countUnsynced() + actionDao().countUnsynced() + transferDao().countUnsynced()

    /** Nombre de saisies en attente, mis à jour en continu (indicateur de synchronisation). */
    fun observePendingCount(): Flow<Int> = combine(
        batchDao().observeUnsyncedCount(),
        actionDao().observeUnsyncedCount(),
        transferDao().observeUnsyncedCount(),
    ) { a, b, c -> a + b + c }

    /** Conflits et refus à examiner. */
    fun observeProblemCount(): Flow<Int> = combine(
        batchDao().observeProblemCount(),
        actionDao().observeRejectedCount(),
        transferDao().observeRejectedCount(),
    ) { a, b, c -> a + b + c }

    // Pas de convertisseurs : dates en Long, énumérations en String.

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "taqnin_id_batch.db"

        /**
         * Version 1 → 2. Les lots, actions et transferts déjà envoyés
         * (isSynced = 1) passent à l'état SYNCED, les autres restent en attente.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE batches ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE batches ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE batches ADD COLUMN syncMessage TEXT")
                db.execSQL("ALTER TABLE batches ADD COLUMN createdBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE batches ADD COLUMN updatedBy TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE batches ADD COLUMN parentTaqninId TEXT")
                db.execSQL("UPDATE batches SET syncState = 'SYNCED' WHERE isSynced = 1")
                // Un lot créé en version 1 l'a été par son propriétaire
                db.execSQL("UPDATE batches SET createdBy = ownerId, updatedBy = ownerId")

                db.execSQL("ALTER TABLE batch_actions ADD COLUMN orgId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE batch_actions ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE batch_actions ADD COLUMN syncMessage TEXT")
                db.execSQL("UPDATE batch_actions SET syncState = 'SYNCED' WHERE isSynced = 1")
                db.execSQL(
                    "UPDATE batch_actions SET orgId = " +
                        "COALESCE((SELECT licenseNumber FROM batches WHERE batches.id = batch_actions.batchId), '')"
                )

                db.execSQL("ALTER TABLE transfers ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE transfers ADD COLUMN syncMessage TEXT")
                db.execSQL("UPDATE transfers SET syncState = 'SYNCED' WHERE isSynced = 1")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
