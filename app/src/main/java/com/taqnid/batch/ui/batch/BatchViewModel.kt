package com.taqnid.batch.ui.batch

import android.app.Application
import androidx.lifecycle.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.*
import com.taqnid.batch.data.remote.FirebaseRepository
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.BusinessRuleException
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.worker.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * ViewModel partagé entre les fragments de gestion des lots.
 *
 * Toutes les écritures passent par l'utilisateur connecté (rôle et
 * organisation issus de son profil serveur) ; un appui répété pendant une
 * écriture est ignoré.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = BatchRepository(db)
    private val userRepository = UserRepository(application)
    private val remote by lazy { FirebaseRepository() }

    val currentUser: User? get() = userRepository.getCachedUser()

    // ── Listes observables ─────────────────────────────────────────────────────

    val allBatches = repository.observeAllBatches().asLiveData()
    val recentBatches = repository.observeRecentBatches(10).asLiveData()
    val activeBatchCount = repository.observeActiveBatchCount().asLiveData()
    val expiringBatches = repository.observeExpiringBatches(7).asLiveData()
    val lowStockBatches = repository.observeLowStockBatches(50.0).asLiveData()
    val transferableBatches = repository.observeTransferableBatches().asLiveData()

    // ── Recherche ──────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchResults: LiveData<List<Batch>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAllBatches()
            else repository.searchBatches(query)
        }.asLiveData()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ── Lot sélectionné ────────────────────────────────────────────────────────

    private val _selectedBatchId = MutableLiveData<String?>()

    val selectedBatch: LiveData<Batch?> = _selectedBatchId.switchMap { id ->
        if (id == null) MutableLiveData(null)
        else repository.observeBatchById(id).asLiveData()
    }

    val selectedBatchActions: LiveData<List<BatchAction>> = _selectedBatchId.switchMap { id ->
        if (id == null) MutableLiveData(emptyList())
        else repository.observeActionsForBatch(id).asLiveData()
    }

    fun selectBatch(batchId: String) {
        _selectedBatchId.value = batchId
    }

    // ── États UI ──────────────────────────────────────────────────────────────

    private val _operationResult = MutableLiveData<Event<Result<String>>>()
    val operationResult: LiveData<Event<Result<String>>> = _operationResult

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    /** Exécute une écriture une seule fois à la fois, puis demande une synchronisation. */
    private fun write(success: String, block: suspend (User) -> Unit) {
        if (_busy.value == true) return
        val user = currentUser
        if (user == null) {
            _operationResult.value = Event(Result.failure(BusinessRuleException("Session expirée : reconnectez-vous")))
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                block(user)
                _operationResult.value = Event(Result.success(success))
                SyncWorker.syncNow(getApplication())
            } catch (e: Exception) {
                _operationResult.value = Event(Result.failure(e))
            } finally {
                _busy.value = false
            }
        }
    }

    // ── Écritures ─────────────────────────────────────────────────────────────

    fun createBatch(
        variety: String,
        type: BatchType,
        initialQuantity: Double?,
        unit: String,
        locationName: String,
        notes: String = ""
    ) = write("Lot créé") { user ->
        repository.createBatch(user, variety, type, initialQuantity, unit, "", locationName, notes)
    }

    fun changeStage(batchId: String, newStage: BatchStage) = write("Stade mis à jour : ${newStage.labelFr}") { user ->
        repository.changeStage(user, batchId, newStage)
    }

    fun recordAction(batchId: String, actionType: ActionType, newQuantity: Double?, description: String) =
        write("${actionType.labelFr} enregistrée") { user ->
            repository.recordAction(user, batchId, actionType, newQuantity, description)
        }

    /** Conflit : reprendre l'état serveur (le journal local est conservé et envoyé). */
    fun keepServerVersion(batch: Batch) = write("Version du serveur reprise") {
        val server = remote.downloadBatch(batch.taqninId)
            ?: throw BusinessRuleException("Lot introuvable sur le serveur")
        repository.keepServerVersion(server)
    }

    /** Conflit : réécrire la saisie locale par-dessus la version serveur actuelle. */
    fun keepLocalVersion(batch: Batch) = write("Votre saisie sera renvoyée") {
        val server = remote.downloadBatch(batch.taqninId)
            ?: throw BusinessRuleException("Lot introuvable sur le serveur")
        repository.keepLocalVersion(batch.id, server.version)
    }

    // ── Lookup par scan QR ─────────────────────────────────────────────────────

    private val _scannedBatch = MutableLiveData<Batch?>()
    val scannedBatch: LiveData<Batch?> = _scannedBatch

    fun lookupByTaqninId(taqninId: String) {
        viewModelScope.launch {
            _scannedBatch.value = repository.getBatchByTaqninId(taqninId)
        }
    }

    fun clearScannedBatch() {
        _scannedBatch.value = null
    }
}

/** Valeur consommée une seule fois (message après une écriture). */
class Event<out T>(private val content: T) {
    private var handled = false
    fun take(): T? = if (handled) null else { handled = true; content }
}
