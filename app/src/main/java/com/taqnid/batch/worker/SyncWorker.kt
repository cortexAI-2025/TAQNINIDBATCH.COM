package com.taqnid.batch.worker

import android.content.Context
import androidx.work.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.remote.FirebaseRepository
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.TransferRepository
import java.util.concurrent.TimeUnit

/**
 * Worker de synchronisation hors-ligne → Firebase Firestore.
 * Se déclenche automatiquement dès qu'une connexion réseau est disponible.
 *
 * Processus :
 *   1. Récupère tous les lots non synchronisés depuis Room.
 *   2. Les envoie sur Firestore.
 *   3. Marque chaque entité comme synchronisée.
 *   4. Répète pour les actions et les transferts.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = AppDatabase.getInstance(context)
    private val firebaseRepo = FirebaseRepository()
    private val batchRepo = BatchRepository(db.batchDao(), db.actionDao())
    private val transferRepo = TransferRepository(db.transferDao())

    override suspend fun doWork(): Result {
        return try {
            syncBatches()
            syncActions()
            syncTransfers()
            Result.success()
        } catch (e: Exception) {
            // Réessaie jusqu'à 3 fois si échec réseau
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Erreur inconnue"))
                )
            }
        }
    }

    private suspend fun syncBatches() {
        val unsyncedBatches = batchRepo.getUnsyncedBatches()
        unsyncedBatches.forEach { batch ->
            firebaseRepo.uploadBatch(batch)
            batchRepo.markBatchSynced(batch.id)
        }
    }

    private suspend fun syncActions() {
        val unsyncedActions = batchRepo.getUnsyncedActions()
        unsyncedActions.forEach { action ->
            firebaseRepo.uploadAction(action)
            batchRepo.markActionSynced(action.id)
        }
    }

    private suspend fun syncTransfers() {
        val unsyncedTransfers = transferRepo.getUnsyncedTransfers()
        unsyncedTransfers.forEach { transfer ->
            firebaseRepo.uploadTransfer(transfer)
            transferRepo.markTransferSynced(transfer.id)
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "taqnin_sync_periodic"
        const val WORK_NAME_ONE_TIME = "taqnin_sync_immediate"

        /**
         * Contraintes : nécessite une connexion réseau.
         */
        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Planifie une synchronisation périodique toutes les 15 minutes (minimum WorkManager).
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Déclenche une synchronisation immédiate (ex : après reconnexion réseau).
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /**
         * Annule toutes les synchronisations planifiées.
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONE_TIME)
        }
    }
}
