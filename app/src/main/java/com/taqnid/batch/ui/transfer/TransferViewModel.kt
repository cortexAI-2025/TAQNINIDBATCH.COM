package com.taqnid.batch.ui.transfer

import android.app.Application
import androidx.lifecycle.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.*
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.TransferRepository
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion des transferts.
 */
class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val transferRepo = TransferRepository(db.transferDao())
    private val batchRepo = BatchRepository(db.batchDao(), db.actionDao())

    val allTransfers = transferRepo.observeAll().asLiveData()
    val inTransitTransfers = transferRepo.observeInTransit().asLiveData()

    private val _selectedTransferId = MutableLiveData<String?>()
    val selectedTransfer: LiveData<Transfer?> = _selectedTransferId.switchMap { id ->
        if (id == null) MutableLiveData(null)
        else transferRepo.observeById(id).asLiveData()
    }

    private val _operationResult = MutableLiveData<Result<String>>()
    val operationResult: LiveData<Result<String>> = _operationResult

    // Lots sélectionnés pour le nouveau transfert
    private val _selectedBatches = MutableLiveData<List<Batch>>(emptyList())
    val selectedBatches: LiveData<List<Batch>> = _selectedBatches

    fun selectTransfer(id: String) {
        _selectedTransferId.value = id
    }

    fun addBatchToTransfer(batch: Batch) {
        val current = _selectedBatches.value?.toMutableList() ?: mutableListOf()
        if (current.none { it.id == batch.id }) current.add(batch)
        _selectedBatches.value = current
    }

    fun removeBatchFromTransfer(batchId: String) {
        _selectedBatches.value = _selectedBatches.value?.filter { it.id != batchId }
    }

    fun clearSelectedBatches() {
        _selectedBatches.value = emptyList()
    }

    fun createTransfer(
        originLicenseId: String,
        originLicenseName: String,
        destinationLicenseId: String,
        destinationLicenseName: String,
        driverName: String,
        vehiclePlate: String,
        createdBy: String,
        createdByName: String,
        notes: String = ""
    ) {
        val batches = _selectedBatches.value ?: emptyList()
        if (batches.isEmpty()) {
            _operationResult.value = Result.failure(Exception("Aucun lot sélectionné"))
            return
        }

        viewModelScope.launch {
            try {
                val transfer = transferRepo.createTransfer(
                    originLicenseId, originLicenseName,
                    destinationLicenseId, destinationLicenseName,
                    batches, driverName, vehiclePlate,
                    createdBy, createdByName, notes
                )

                // Marquer les lots comme transférés
                batches.forEach { batch ->
                    batchRepo.recordAction(
                        batch = batch,
                        actionType = ActionType.TRANSFERT_SORTANT,
                        newQuantity = batch.currentQuantity,
                        performedBy = createdBy,
                        performedByName = createdByName,
                        description = "Inclus dans le transfert ${transfer.transferNumber}"
                    )
                }

                clearSelectedBatches()
                _operationResult.value = Result.success("Transfert ${transfer.transferNumber} créé")
            } catch (e: Exception) {
                _operationResult.value = Result.failure(e)
            }
        }
    }

    fun receiveTransfer(
        transfer: Transfer,
        receivedBy: String,
        receivedQuantities: Map<String, Double>
    ) {
        viewModelScope.launch {
            try {
                val updatedTransfer = transferRepo.receiveTransfer(transfer, receivedBy, receivedQuantities)

                // Mettre à jour la propriété de chaque lot reçu
                transfer.batchIds.forEach { taqninId ->
                    val batch = batchRepo.getBatchByTaqninId(taqninId) ?: return@forEach
                    val receivedQty = receivedQuantities[taqninId] ?: batch.currentQuantity
                    batchRepo.recordAction(
                        batch = batch,
                        actionType = ActionType.TRANSFERT_ENTRANT,
                        newQuantity = receivedQty,
                        performedBy = receivedBy,
                        performedByName = receivedBy,
                        description = "Réceptionné — transfert ${transfer.transferNumber}"
                    )
                }

                _operationResult.value = Result.success("Transfert réceptionné avec succès")
            } catch (e: Exception) {
                _operationResult.value = Result.failure(e)
            }
        }
    }
}
