package com.taqnid.batch.ui.batch

import android.app.Application
import androidx.lifecycle.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.*
import com.taqnid.batch.repository.BatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * ViewModel partagé entre les fragments de gestion des lots.
 */
class BatchViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = BatchRepository(db.batchDao(), db.actionDao())

    // ── Listes observables ─────────────────────────────────────────────────────

    val allBatches = repository.observeAllBatches().asLiveData()
    val recentBatches = repository.observeRecentBatches(10).asLiveData()
    val activeBatchCount = repository.observeActiveBatchCount().asLiveData()
    val expiringBatches = repository.observeExpiringBatches(7).asLiveData()
    val lowStockBatches = repository.observeLowStockBatches(50.0).asLiveData()

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

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    // ── Création d'un lot ──────────────────────────────────────────────────────

    fun createBatch(
        variety: String,
        type: BatchType,
        initialQuantity: Double,
        unit: String,
        locationId: String,
        locationName: String,
        ownerId: String,
        ownerName: String,
        licenseNumber: String,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                val batch = repository.createBatch(
                    variety, type, initialQuantity, unit,
                    locationId, locationName, ownerId, ownerName, licenseNumber, notes
                )
                _operationResult.value = Result.success("Lot ${batch.taqninId} créé avec succès")
            } catch (e: Exception) {
                _operationResult.value = Result.failure(e)
            }
        }
    }

    // ── Changement de stade ────────────────────────────────────────────────────

    fun changeStage(
        batch: Batch,
        newStage: BatchStage,
        ownerId: String,
        ownerName: String,
        description: String = ""
    ) {
        viewModelScope.launch {
            try {
                repository.changeStage(batch, newStage, ownerId, ownerName, description)
                _operationResult.value = Result.success("Stade mis à jour : ${newStage.labelFr}")
            } catch (e: Exception) {
                _operationResult.value = Result.failure(e)
            }
        }
    }

    // ── Enregistrement d'une action ────────────────────────────────────────────

    fun recordAction(
        batch: Batch,
        actionType: ActionType,
        newQuantity: Double,
        ownerId: String,
        ownerName: String,
        description: String
    ) {
        viewModelScope.launch {
            try {
                repository.recordAction(batch, actionType, newQuantity, ownerId, ownerName, description)
                _operationResult.value = Result.success("Action enregistrée")
            } catch (e: Exception) {
                _operationResult.value = Result.failure(e)
            }
        }
    }

    // ── Lookup par scan QR ─────────────────────────────────────────────────────

    private val _scannedBatch = MutableLiveData<Batch?>()
    val scannedBatch: LiveData<Batch?> = _scannedBatch

    fun lookupByTaqninId(taqninId: String) {
        viewModelScope.launch {
            val batch = repository.getBatchByTaqninId(taqninId)
            _scannedBatch.value = batch
        }
    }

    fun clearScannedBatch() {
        _scannedBatch.value = null
    }
}
