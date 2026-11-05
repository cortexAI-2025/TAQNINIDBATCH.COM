package com.taqnid.batch.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.local.entity.BatchEntity
import com.taqnid.batch.data.model.*
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.BusinessRuleException
import com.taqnid.batch.repository.TransferRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Application neutre : celle de l'appli planifie WorkManager au démarrage
@Config(application = android.app.Application::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var batches: BatchRepository
    private lateinit var transfers: TransferRepository

    private fun user(role: UserRole, org: String = "ORG-A", active: Boolean = true) =
        User(uid = "u-$role-$org", email = "x@test", displayName = "$role $org", role = role, licenseId = org,
            licenseName = "Organisation $org", isActive = active)

    private val manager = user(UserRole.MANAGER)
    private val ouvrier = user(UserRole.OUVRIER)
    private val lecteur = user(UserRole.LECTEUR)
    private val managerB = user(UserRole.MANAGER, "ORG-B")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        batches = BatchRepository(db)
        transfers = TransferRepository(db, batches)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun refused(block: suspend () -> Unit): String {
        try {
            block()
        } catch (e: BusinessRuleException) {
            return e.message ?: ""
        }
        fail("La saisie aurait dû être refusée")
        return ""
    }

    private suspend fun newBatch(qty: Double = 1000.0) =
        batches.createBatch(manager, "Beldiya", BatchType.GRAINE, qty, "g", "", "Serre 1")

    @Test
    fun `creation - lot et action de creation ecrits ensemble, rattaches a l'organisation du compte`() = runBlocking<Unit> {
        val batch = newBatch()
        val stored = db.batchDao().getById(batch.id)!!
        assertEquals("ORG-A", stored.licenseNumber)
        assertEquals(manager.uid, stored.createdBy)
        assertEquals(SyncState.PENDING.name, stored.syncState)
        assertEquals(0L, stored.version)
        val actions = db.actionDao().getActionsForBatch(batch.id)
        assertEquals(1, actions.size)
        assertEquals("ORG-A", actions.single().orgId)
    }

    @Test
    fun `roles - lecteur et ouvrier ne creent pas de lot, lecteur n'enregistre rien`() = runBlocking<Unit> {
        refused { batches.createBatch(lecteur, "Beldiya", BatchType.GRAINE, 10.0, "g", "", "S") }
        refused { batches.createBatch(ouvrier, "Beldiya", BatchType.GRAINE, 10.0, "g", "", "S") }
        val batch = newBatch()
        refused { batches.recordAction(lecteur, batch.id, ActionType.PESEE, 900.0, "") }
        refused { batches.changeStage(ouvrier, batch.id, BatchStage.VEGETATION) }
        // L'ouvrier pèse
        batches.recordAction(ouvrier, batch.id, ActionType.PESEE, 900.0, "Pesée")
        assertEquals(900.0, db.batchDao().getById(batch.id)!!.currentQuantity, 0.0)
    }

    @Test
    fun `compte inactif ou sans organisation - aucune saisie`() = runBlocking<Unit> {
        refused { batches.createBatch(user(UserRole.MANAGER, active = false), "B", BatchType.GRAINE, 1.0, "g", "", "S") }
        refused { batches.createBatch(user(UserRole.MANAGER, org = ""), "B", BatchType.GRAINE, 1.0, "g", "", "S") }
    }

    @Test
    fun `quantites et unites controlees avant ecriture`() = runBlocking<Unit> {
        refused { batches.createBatch(manager, "B", BatchType.GRAINE, -5.0, "g", "", "S") }
        refused { batches.createBatch(manager, "B", BatchType.GRAINE, null, "g", "", "S") }
        refused { batches.createBatch(manager, "B", BatchType.GRAINE, 5.0, "tonnes", "", "S") }
        val batch = newBatch()
        refused { batches.recordAction(ouvrier, batch.id, ActionType.PESEE, -1.0, "") }
        refused { batches.recordAction(ouvrier, batch.id, ActionType.PERTE, 1200.0, "") }
        refused { batches.recordAction(ouvrier, batch.id, ActionType.PESEE, null, "") }
        // Rien n'a été écrit
        assertEquals(1000.0, db.batchDao().getById(batch.id)!!.currentQuantity, 0.0)
        assertEquals(1, db.actionDao().getActionsForBatch(batch.id).size)
    }

    @Test
    fun `transitions de stade - en avant, rien apres destruction`() = runBlocking<Unit> {
        val batch = newBatch()
        batches.changeStage(manager, batch.id, BatchStage.FLORAISON)
        refused { batches.changeStage(manager, batch.id, BatchStage.VEGETATION) }
        batches.changeStage(manager, batch.id, BatchStage.DETRUIT)
        refused { batches.changeStage(manager, batch.id, BatchStage.RECOLTE) }
        refused { batches.recordAction(ouvrier, batch.id, ActionType.PESEE, 10.0, "") }
    }

    @Test
    fun `une pesee apres synchronisation remet le lot en attente d'envoi`() = runBlocking<Unit> {
        val batch = newBatch()
        val stored = db.batchDao().getById(batch.id)!!
        assertEquals(1, db.batchDao().markSyncedIfUnchanged(batch.id, stored.lastUpdated, 1L))
        assertEquals(0, db.batchDao().countUnsynced())
        Thread.sleep(2)
        batches.recordAction(ouvrier, batch.id, ActionType.PESEE, 990.0, "")
        assertEquals(1, db.batchDao().countUnsynced())
        assertEquals(1L, db.batchDao().getById(batch.id)!!.version)
    }

    @Test
    fun `saisie pendant l'envoi - elle n'est pas marquee envoyee, rien n'est perdu`() = runBlocking<Unit> {
        val batch = newBatch()
        val readForSending = db.batchDao().getById(batch.id)!!
        Thread.sleep(2)
        batches.recordAction(ouvrier, batch.id, ActionType.PESEE, 980.0, "Pendant l'envoi")
        // La confirmation du serveur arrive pour l'état lu avant la pesée
        assertEquals(0, db.batchDao().markSyncedIfUnchanged(batch.id, readForSending.lastUpdated, 1L))
        assertEquals(1, db.batchDao().countUnsynced())
    }

    @Test
    fun `donnees serveur - la mise a jour n'efface pas le journal local (pas de REPLACE en cascade)`() = runBlocking<Unit> {
        val batch = newBatch()
        batches.recordAction(ouvrier, batch.id, ActionType.PESEE, 950.0, "")
        val server = db.batchDao().getById(batch.id)!!.toModel().copy(currentQuantity = 940.0, version = 3)
        batches.keepServerVersion(server)
        assertEquals(940.0, db.batchDao().getById(batch.id)!!.currentQuantity, 0.0)
        assertEquals(2, db.actionDao().getActionsForBatch(batch.id).size)
    }

    @Test
    fun `conflit - garder sa saisie la renvoie depuis la version serveur`() = runBlocking<Unit> {
        val batch = newBatch()
        db.batchDao().markConflict(batch.id, "Modifié ailleurs")
        assertEquals(0, db.batchDao().getUnsynced().size)
        batches.keepLocalVersion(batch.id, 5L)
        val stored = db.batchDao().getById(batch.id)!!
        assertEquals(SyncState.PENDING.name, stored.syncState)
        assertEquals(5L, stored.version)
        assertEquals(1, db.batchDao().getUnsynced().size)
    }

    @Test
    fun `transfert - sortie de stock atomique, double soumission refusee`() = runBlocking<Unit> {
        val batch = newBatch(200.0)
        val transfer = transfers.createTransfer(manager, "ORG-B", "Coop B", listOf(batch.id), "Chauffeur", "1-A-1")
        assertEquals(0.0, db.batchDao().getById(batch.id)!!.currentQuantity, 0.0)
        assertEquals(TransferStatus.EN_TRANSIT, transfer.status)
        assertTrue(transfer.transferNumber.matches(Regex("^TRF-\\d{4}-[0-9A-Z]{8}$")))
        // Second appui : le lot est vide, rien n'est créé
        refused { transfers.createTransfer(manager, "ORG-B", "Coop B", listOf(batch.id), "Chauffeur", "1-A-1") }
        assertEquals(1, db.transferDao().countUnsynced())
    }

    @Test
    fun `transfert - pas vers soi-meme, pas les lots d'une autre organisation, pas par un ouvrier`() = runBlocking<Unit> {
        val batch = newBatch()
        refused { transfers.createTransfer(manager, "ORG-A", "Moi", listOf(batch.id), "C", "P") }
        refused { transfers.createTransfer(managerB, "ORG-C", "C", listOf(batch.id), "C", "P") }
        refused { transfers.createTransfer(ouvrier, "ORG-B", "B", listOf(batch.id), "C", "P") }
        assertEquals(1000.0, db.batchDao().getById(batch.id)!!.currentQuantity, 0.0)
    }

    @Test
    fun `reception - par le destinataire seulement, une fois, jamais plus que l'expedie`() = runBlocking<Unit> {
        val batch = newBatch(200.0)
        val transfer = transfers.createTransfer(manager, "ORG-B", "Coop B", listOf(batch.id), "Chauffeur", "1-A-1")
        refused { transfers.receiveTransfer(manager, transfer.id, emptyMap()) }
        refused { transfers.receiveTransfer(managerB, transfer.id, mapOf(batch.taqninId to 250.0)) }
        val received = transfers.receiveTransfer(managerB, transfer.id, mapOf(batch.taqninId to 195.0))
        assertEquals(TransferStatus.RECEPTIONNE, received.status)
        assertEquals(managerB.uid, received.receivedBy)
        assertEquals(-5.0, received.batchSummaries.single().discrepancy!!, 0.0)
        refused { transfers.receiveTransfer(managerB, transfer.id, emptyMap()) }
        // Un lot est créé chez le destinataire, relié au lot d'origine
        val created = db.batchDao().getUnsynced().first { it.licenseNumber == "ORG-B" }
        assertEquals(batch.taqninId, created.parentTaqninId)
        assertEquals(195.0, created.initialQuantity, 0.0)
        assertNotEquals(batch.taqninId, created.taqninId)
    }

    @Test
    fun `deconnexion - effacer la base ne laisse rien au compte suivant`() = runBlocking<Unit> {
        newBatch()
        assertTrue(db.pendingCount() > 0)
        db.clearAllTables()
        assertEquals(0, db.pendingCount())
        assertEquals(0, db.batchDao().getUnsynced().size)
    }

    @Test
    fun `le journal ne remplace jamais une action existante`() = runBlocking<Unit> {
        val batch = newBatch()
        val action = db.actionDao().getActionsForBatch(batch.id).single()
        try {
            db.actionDao().insert(action.copy(description = "réécrite"))
            fail("Une action existante a été remplacée")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // attendu
        }
        db.actionDao().insertIfAbsent(action.copy(description = "réécrite"))
        assertEquals("Création du lot — quantité initiale : 1000.0 g",
            db.actionDao().getActionsForBatch(batch.id).single().description)
        Unit
    }

    @Suppress("unused")
    private fun BatchEntity.short() = "$taqninId $currentQuantity"
}
