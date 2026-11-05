package com.taqnid.batch.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.Transfer
import com.taqnid.batch.domain.QrPayload
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.TransferRepository
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
 * ViewModel de l'écran de scan. La recherche se fait dans la base locale :
 * elle fonctionne sans réseau pour tout lot ou transfert déjà synchronisé
 * sur l'appareil.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val batchRepo = BatchRepository(db)
    private val transferRepo = TransferRepository(db)

    private val _scanResult = MutableLiveData<ScanResult>(ScanResult.Idle)
    val scanResult: LiveData<ScanResult> = _scanResult

    private val _isFlashOn = MutableLiveData(false)
    val isFlashOn: LiveData<Boolean> = _isFlashOn

    /**
     * Traite le contenu brut d'un code scanné.
     */
    fun processQrCode(rawContent: String) {
        if (_scanResult.value is ScanResult.Scanning) return // Évite les doublons
        _scanResult.value = ScanResult.Scanning

        viewModelScope.launch {
            _scanResult.value = try {
                when (val payload = QrPayload.parse(rawContent)) {
                    is QrPayload.BatchId -> {
                        val batch = batchRepo.getBatchByTaqninId(payload.taqninId)
                        when {
                            batch != null -> ScanResult.BatchFound(batch)
                            payload.official -> ScanResult.Error(
                                "Lot ${payload.taqninId} de la plateforme web : absent de cet appareil. " +
                                    "Synchronisez, ou vérifiez-le sur la plateforme."
                            )
                            else -> ScanResult.Error("Lot ${payload.taqninId} absent de cet appareil : synchronisez puis réessayez")
                        }
                    }
                    is QrPayload.TransferManifest -> {
                        val transfer = transferRepo.getByNumber(payload.transferNumber)
                        transfer?.let { ScanResult.TransferFound(it) }
                            ?: ScanResult.Error("Transfert ${payload.transferNumber} absent de cet appareil : synchronisez puis réessayez")
                    }
                    is QrPayload.Invalid -> ScanResult.UnknownQR(payload.reason)
                }
            } catch (e: Exception) {
                ScanResult.Error("Erreur de lecture : ${e.message}")
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
