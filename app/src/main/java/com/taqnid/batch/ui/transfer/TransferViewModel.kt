package com.taqnid.batch.ui.transfer

import android.app.Application
import androidx.lifecycle.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.*
import com.taqnid.batch.repository.BusinessRuleException
import com.taqnid.batch.repository.TransferRepository
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.ui.batch.Event
import com.taqnid.batch.worker.SyncWorker
import kotlinx.coroutines.launch

/**
 * ViewModel des transferts. L'expédition et la réception relisent les lots
 * dans une transaction (TransferRepository) : un double appui ne crée ni
 * deux manifestes ni deux sorties de stock.
 */
class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val transferRepo = TransferRepository(db)
    private val userRepository = UserRepository(application)

    val currentUser: User? get() = userRepository.getCachedUser()

    val allTransfers = transferRepo.observeAll().asLiveData()
    val inTransitTransfers = transferRepo.observeInTransit().asLiveData()

    private val _selectedTransferId = MutableLiveData<String?>()
    val selectedTransfer: LiveData<Transfer?> = _selectedTransferId.switchMap { id ->
        if (id == null) MutableLiveData(null)
        else transferRepo.observeById(id).asLiveData()
    }

    private val _operationResult = MutableLiveData<Event<Result<String>>>()
    val operationResult: LiveData<Event<Result<String>>> = _operationResult

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    // Lots sélectionnés pour le nouveau transfert
    private val _selectedBatches = MutableLiveData<List<Batch>>(emptyList())
    val selectedBatches: LiveData<List<Batch>> = _selectedBatches

    fun selectTransfer(id: String) {
        _selectedTransferId.value = id
    }

    fun addBatchToTransfer(batch: Batch) {
        val current = _selectedBatches.value?.toMutableList() ?: mutableListOf()
        if (current.none { it.id == batch.id } && current.size < TransferRepository.MAX_BATCHES) current.add(batch)
        _selectedBatches.value = current
    }

    fun removeBatchFromTransfer(batchId: String) {
        _selectedBatches.value = _selectedBatches.value?.filter { it.id != batchId }
    }

    fun clearSelectedBatches() {
        _selectedBatches.value = emptyList()
    }

    private fun write(block: suspend (User) -> String) {
        if (_busy.value == true) return
        val user = currentUser
        if (user == null) {
            _operationResult.value = Event(Result.failure(BusinessRuleException("Session expirée : reconnectez-vous")))
            return
        }
        _busy.value = true
        viewModelScope.launch {
            try {
                _operationResult.value = Event(Result.success(block(user)))
                SyncWorker.syncNow(getApplication())
            } catch (e: Exception) {
                _operationResult.value = Event(Result.failure(e))
            } finally {
                _busy.value = false
            }
        }
    }

    fun createTransfer(
        destinationLicenseId: String,
        destinationLicenseName: String,
        driverName: String,
        vehiclePlate: String,
        notes: String = ""
    ) = write { user ->
        val transfer = transferRepo.createTransfer(
            user, destinationLicenseId, destinationLicenseName,
            _selectedBatches.value.orEmpty().map { it.id },
            driverName, vehiclePlate, notes
        )
        clearSelectedBatches()
        "Transfert ${transfer.transferNumber} expédié"
    }

    fun receiveTransfer(transferId: String, receivedQuantities: Map<String, Double>) = write { user ->
        transferRepo.receiveTransfer(user, transferId, receivedQuantities)
        "Transfert réceptionné : les lots reçus sont créés dans votre stock"
    }

    fun rejectTransfer(transferId: String) = write { user ->
        transferRepo.rejectTransfer(user, transferId)
        "Transfert refusé"
    }
}
