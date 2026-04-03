package com.taqnid.batch.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.Transfer
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.TransferRepository
import com.taqnid.batch.utils.TaqninIdGenerator
import kotlinx.coroutines.launch

/**
 * Résultat d'un scan QR code.
 */
sealed class ScanResult {
    object Idle : ScanResult()
    object Scanning : ScanResult()
    data class BatchFound(val batch: Batch) : ScanResult()
    data class TransferFound(val transfer: Transfer) : ScanResult()
    data class UnknownQR(val rawContent: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

/**
 * ViewModel pour l'écran de scan QR.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val batchRepo = BatchRepository(db.batchDao(), db.actionDao())
    private val transferRepo = TransferRepository(db.transferDao())

    private val _scanResult = MutableLiveData<ScanResult>(ScanResult.Idle)
    val scanResult: LiveData<ScanResult> = _scanResult

    private val _isFlashOn = MutableLiveData(false)
    val isFlashOn: LiveData<Boolean> = _isFlashOn

    /**
     * Traite le contenu brut d'un QR code scanné.
     */
    fun processQrCode(rawContent: String) {
        if (_scanResult.value is ScanResult.Scanning) return // Évite les doublons
        _scanResult.value = ScanResult.Scanning

        viewModelScope.launch {
            try {
                when {
                    // Taqnin ID simple (lot individuel)
                    TaqninIdGenerator.isValid(rawContent) -> {
                        val batch = batchRepo.getBatchByTaqninId(rawContent)
                        _scanResult.value = if (batch != null) {
                            ScanResult.BatchFound(batch)
                        } else {
                            ScanResult.Error("Lot introuvable : $rawContent")
                        }
                    }

                    // QR de manifeste de transfert (format : TRF:...)
                    rawContent.startsWith("TRF:") -> {
                        val transferNumber = rawContent.substringAfter("TRF:").substringBefore("|")
                        // Recherche par numéro de transfert dans les transferts en transit
                        val transfers = transferRepo.getUnsyncedTransfers()
                        val transfer = transfers.firstOrNull { it.transferNumber == transferNumber }
                        _scanResult.value = if (transfer != null) {
                            ScanResult.TransferFound(transfer)
                        } else {
                            ScanResult.UnknownQR(rawContent)
                        }
                    }

                    else -> {
                        _scanResult.value = ScanResult.UnknownQR(rawContent)
                    }
                }
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error("Erreur de traitement : ${e.message}")
            }
        }
    }

    fun resetScan() {
        _scanResult.value = ScanResult.Idle
    }

    fun toggleFlash() {
        _isFlashOn.value = !(_isFlashOn.value ?: false)
    }
}
