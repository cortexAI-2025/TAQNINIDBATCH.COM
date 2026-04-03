package com.taqnid.batch.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.taqnid.batch.data.local.dao.*
import com.taqnid.batch.data.local.entity.*

/**
 * Base de données Room principale — version 1.
 * Les migrations futures seront ajoutées ici.
 */
@Database(
    entities = [
        BatchEntity::class,
        ActionEntity::class,
        TransferEntity::class,
        LocationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun batchDao(): BatchDao
    abstract fun actionDao(): ActionDao
    abstract fun transferDao(): TransferDao
    abstract fun locationDao(): LocationDao

    // ── Convertisseurs de types ────────────────────────────────────────────────
    class Converters {
        // Room n'a pas besoin de convertisseurs supplémentaires pour ce projet
        // car les dates sont stockées en Long et les enums en String.
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DB_NAME = "taqnin_id_batch.db"

        /**
         * Migration exemple de la version 1 → 2 (à adapter selon les besoins futurs).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Exemple : db.execSQL("ALTER TABLE batches ADD COLUMN newField TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration() // Pour le développement uniquement
                    // .addMigrations(MIGRATION_1_2)  // Décommenter en production
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
