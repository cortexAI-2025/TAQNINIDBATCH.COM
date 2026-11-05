package com.taqnid.batch.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.local.entity.ActionEntity
import com.taqnid.batch.data.local.entity.BatchEntity
import com.taqnid.batch.data.local.entity.TransferEntity
import com.taqnid.batch.data.model.SyncState
import com.taqnid.batch.data.remote.FirebaseRepository
import com.taqnid.batch.domain.SyncDecision
import com.taqnid.batch.repository.UserRepository
import java.util.concurrent.TimeUnit

/**
 * Synchronisation Room ↔ Firestore.
 *
 * Envoi, dans l'ordre : lots (avec leurs premières actions), actions,
 * transferts. Puis récupération des lots, actions et transferts de
 * l'organisation (réinstallation, autre appareil).
 *
 * Garanties :
 *  - rien n'est marqué envoyé avant la confirmation du serveur, et seulement
 *    si l'élément n'a pas été modifié pendant l'envoi ;
 *  - chaque envoi est rejouable sans doublon (identifiants fixés sur
 *    l'appareil, écritures identiques acceptées par les règles) ;
 *  - une modification concurrente du même lot devient un CONFLIT visible,
 *    jamais un écrasement ;
 *  - un refus du serveur (droits, données invalides) est conservé avec son
 *    motif (REFUSÉ), jamais effacé, et ne bloque pas les autres envois ;
 *  - une erreur réseau relance plus tard, sans limite de tentatives : une
 *    coupure de plusieurs heures ne perd rien.
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = AppDatabase.getInstance(context)
    private val remote = FirebaseRepository()
    private val prefs = context.getSharedPreferences("taqnin_sync", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            val user = UserRepository(applicationContext).refreshProfile()
            if (user == null || !user.isActive || user.licenseId.isBlank()) {
                // Compte désactivé ou détaché : on n'envoie rien, les saisies restent sur l'appareil
                return Result.success(workDataOf(KEY_MESSAGE to "Compte inactif : synchronisation suspendue"))
            }
            pushBatches()
            pushActions()
            pushTransfers()
            pull(user.licenseId)
            prefs.edit().putLong(KEY_LAST_SUCCESS, System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            if (isTransient(e)) Result.retry()
            else Result.failure(workDataOf(KEY_MESSAGE to (e.message ?: "Erreur inconnue")))
        }
    }

    private fun isTransient(e: Throwable): Boolean = when (e) {
        is FirebaseNetworkException -> true
        is FirebaseFirestoreException -> e.code in setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.UNKNOWN,
            FirebaseFirestoreException.Code.CANCELLED,
        )
        else -> e.cause?.let { isTransient(it) } ?: false
    }

    private fun refusal(e: Exception): String = when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Refusé par le serveur : droits insuffisants pour cette saisie"
        FirebaseFirestoreException.Code.INVALID_ARGUMENT -> "Refusé par le serveur : données invalides"
        else -> "Refusé par le serveur : ${e.message ?: "erreur"}"
    }

    /** Une erreur réseau remonte (relance) ; un refus est noté sur l'élément. */
    private inline fun <T> orRefused(block: () -> T, onRefused: (String) -> Unit): T? = try {
        block()
    } catch (e: Exception) {
        if (isTransient(e)) throw e
        onRefused(refusal(e))
        null
    }

    private suspend fun pushBatches() {
        val batchDao = db.batchDao()
        val actionDao = db.actionDao()
        for (entity in batchDao.getUnsynced()) {
            val batch = entity.toModel()
            val pending = actionDao.getUnsyncedForBatch(batch.id).map { it.toModel() }
            val decision = orRefused({ remote.pushBatch(batch, pending) }) { message ->
                batchDao.markRejected(batch.id, message)
            } ?: continue
            when (decision) {
                is SyncDecision.Create -> {
                    db.withTransaction {
                        if (batchDao.markSyncedIfUnchanged(batch.id, entity.lastUpdated, 1L) == 0) batchDao.setBaseVersion(batch.id, 1L)
                        pending.forEach { actionDao.markSynced(it.id) }
                    }
                }
                is SyncDecision.Update -> confirm(batch.id, entity.lastUpdated, decision.nextVersion)
                is SyncDecision.AlreadyApplied -> confirm(batch.id, entity.lastUpdated, decision.serverVersion)
                is SyncDecision.Conflict -> batchDao.markConflict(batch.id, decision.message)
            }
        }
    }

    private suspend fun confirm(batchId: String, lastUpdated: Long, version: Long) {
        val batchDao = db.batchDao()
        // Modifié pendant l'envoi : reste en attente, à partir de la nouvelle version serveur
        if (batchDao.markSyncedIfUnchanged(batchId, lastUpdated, version) == 0) batchDao.setBaseVersion(batchId, version)
    }

    private suspend fun pushActions() {
        val actionDao = db.actionDao()
        val batchDao = db.batchDao()
        for (entity in actionDao.getUnsynced()) {
            // Une action attend que son lot existe sur le serveur
            val batch = batchDao.getById(entity.batchId) ?: continue
            if (batch.version == 0L) continue
            orRefused({ remote.uploadAction(entity.toModel()); actionDao.markSynced(entity.id) }) { message ->
                actionDao.markRejected(entity.id, message)
            }
        }
    }

    private suspend fun pushTransfers() {
        val transferDao = db.transferDao()
        for (entity in transferDao.getUnsynced()) {
            orRefused({
                remote.pushTransfer(entity.toModel())
                transferDao.markSyncedIfUnchanged(entity.id, entity.status)
            }) { message -> transferDao.markRejected(entity.id, message) }
        }
    }

    /**
     * Récupère les données de l'organisation. Une donnée locale en attente,
     * en conflit ou refusée n'est jamais remplacée par la version serveur.
     */
    private suspend fun pull(org: String) {
        val batchDao = db.batchDao()
        val actionDao = db.actionDao()
        val transferDao = db.transferDao()

        for (batch in remote.fetchOrgBatches(org)) {
            val local = batchDao.getByTaqninId(batch.taqninId)
            if (local == null || (local.isSynced && local.syncState == SyncState.SYNCED.name)) {
                batchDao.upsert(BatchEntity.fromModel(batch.copy(id = local?.id ?: batch.id)))
            }
        }

        // Fenêtre glissante : tout depuis le dernier succès, avec une marge d'un jour
        val since = (prefs.getLong(KEY_LAST_SUCCESS, 0L) - TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0L)
        for (action in remote.fetchOrgActionsSince(org, since)) {
            if (batchDao.getById(action.batchId) != null) actionDao.insertIfAbsent(ActionEntity.fromModel(action))
        }

        for (transfer in remote.fetchOrgTransfers(org)) {
            val local = transferDao.getById(transfer.id)
            if (local == null || (local.isSynced && local.syncState == SyncState.SYNCED.name)) {
                transferDao.upsert(TransferEntity.fromModel(transfer))
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "taqnin_sync_periodic"
        const val WORK_NAME_ONE_TIME = "taqnin_sync_immediate"
        const val KEY_MESSAGE = "message"
        const val KEY_LAST_SUCCESS = "last_success"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Synchronisation périodique toutes les 15 minutes (minimum WorkManager). */
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
         * Synchronisation immédiate (après une saisie ou au retour du réseau).
         * KEEP : un envoi en cours n'est pas interrompu au milieu.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun lastSuccess(context: Context): Long =
            context.getSharedPreferences("taqnin_sync", Context.MODE_PRIVATE).getLong(KEY_LAST_SUCCESS, 0L)

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_ONE_TIME)
        }
    }
}
