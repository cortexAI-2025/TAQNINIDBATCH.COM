package com.taqnid.batch.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Une base version 1 contenant des saisies déjà envoyées et d'autres en
 * attente passe en version 2 sans rien perdre, et Room valide le schéma
 * obtenu (une colonne manquante ou mal typée ferait échouer l'ouverture).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    private fun createVersion1() {
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Version1Schema.STATEMENTS.forEach { db.execSQL(it) }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO batches VALUES ('b1','TAQ-A3F2-2024','Beldiya','GRAINE',1000,950,'g','','Serre 1','FLORAISON'," +
                "'EN_ATTENTE','u1','Hassan','ORG-A',1,2,NULL,'',1)"
        )
        db.execSQL(
            "INSERT INTO batches VALUES ('b2','TAQ-B7K9-2024','Beldiya','GRAINE',500,500,'g','','Serre 2','GERMINATION'," +
                "'EN_ATTENTE','u1','Hassan','ORG-A',1,3,NULL,'',0)"
        )
        db.execSQL(
            "INSERT INTO batch_actions VALUES ('a1','b1','TAQ-A3F2-2024','PESEE','u1','Hassan',2,1000,950,-50,'g'," +
                "NULL,NULL,'Pesée',''," + "1)"
        )
        db.execSQL(
            "INSERT INTO batch_actions VALUES ('a2','b2','TAQ-B7K9-2024','NOTE','u1','Hassan',3,NULL,NULL,NULL,'g'," +
                "NULL,NULL,'Note non envoyée',''," + "0)"
        )
        helper.close()
    }

    @Test
    fun `migration 1 vers 2 sans perte, schéma validé par Room`() = runBlocking {
        createVersion1()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val synced = db.batchDao().getById("b1")!!
        val pending = db.batchDao().getById("b2")!!
        assertEquals(SyncState.SYNCED.name, synced.syncState)
        assertEquals(SyncState.PENDING.name, pending.syncState)
        assertEquals("u1", synced.createdBy)
        assertEquals(950.0, synced.currentQuantity, 0.0)

        // L'action non envoyée reste à envoyer, avec l'organisation de son lot
        val unsent = db.actionDao().getUnsynced()
        assertEquals(listOf("a2"), unsent.map { it.id })
        assertEquals("ORG-A", unsent.single().orgId)
        assertEquals(1, db.batchDao().countUnsynced())
        db.close()
    }
}
