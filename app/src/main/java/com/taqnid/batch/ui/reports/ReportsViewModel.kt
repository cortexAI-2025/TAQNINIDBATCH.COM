package com.taqnid.batch.ui.reports

import android.app.Application
import androidx.lifecycle.*
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.BatchAction
import com.taqnid.batch.data.model.Transfer
import com.taqnid.batch.repository.BatchRepository
import com.taqnid.batch.repository.TransferRepository
import com.taqnid.batch.utils.CsvExporter
import com.taqnid.batch.utils.PdfExporter
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/**
 * ViewModel pour l'écran de génération de rapports.
 */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val batchRepo = BatchRepository(db.batchDao(), db.actionDao())
    private val transferRepo = TransferRepository(db.transferDao())

    private val _fromDate = MutableLiveData<Date>(
        Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.time
    )
    val fromDate: LiveData<Date> = _fromDate

    private val _toDate = MutableLiveData<Date>(Date())
    val toDate: LiveData<Date> = _toDate

    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    private val _generatedFile = MutableLiveData<Result<File>?>()
    val generatedFile: LiveData<Result<File>?> = _generatedFile

    // Statistiques de la période
    private val _stats = MutableLiveData<PeriodStats?>()
    val stats: LiveData<PeriodStats?> = _stats

    fun setFromDate(date: Date) { _fromDate.value = date }
    fun setToDate(date: Date) { _toDate.value = date }

    fun loadStats() {
        val from = _fromDate.value ?: return
        val to = _toDate.value ?: return
        viewModelScope.launch {
            try {
                val batches = batchRepo.getBatchesInPeriod(from, to)
                val actions = batchRepo.getActionsInPeriod(from, to)
                val transfers = transferRepo.getTransfersInPeriod(from, to)
                _stats.value = PeriodStats(
                    batchCount = batches.size,
                    totalInitialQty = batches.sumOf { it.initialQuantity },
                    totalCurrentQty = batches.sumOf { it.currentQuantity },
                    actionCount = actions.size,
                    transferCount = transfers.size
                )
            } catch (e: Exception) {
                _stats.value = null
            }
        }
    }

    fun generateBatchReportPdf() {
        val from = _fromDate.value ?: return
        val to = _toDate.value ?: return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val batches = batchRepo.getBatchesInPeriod(from, to)
                val actions = batchRepo.getActionsInPeriod(from, to)
                val file = PdfExporter.exportBatchReport(getApplication(), batches, actions, from, to)
                _generatedFile.value = Result.success(file)
            } catch (e: Exception) {
                _generatedFile.value = Result.failure(e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateTransferReportPdf() {
        val from = _fromDate.value ?: return
        val to = _toDate.value ?: return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val transfers = transferRepo.getTransfersInPeriod(from, to)
                val file = PdfExporter.exportTransferReport(getApplication(), transfers, from, to)
                _generatedFile.value = Result.success(file)
            } catch (e: Exception) {
                _generatedFile.value = Result.failure(e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateBatchReportCsv() {
        val from = _fromDate.value ?: return
        val to = _toDate.value ?: return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val batches = batchRepo.getBatchesInPeriod(from, to)
                val file = CsvExporter.exportBatches(getApplication(), batches, from, to)
                _generatedFile.value = Result.success(file)
            } catch (e: Exception) {
                _generatedFile.value = Result.failure(e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun generateActionReportCsv() {
        val from = _fromDate.value ?: return
        val to = _toDate.value ?: return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val actions = batchRepo.getActionsInPeriod(from, to)
                val file = CsvExporter.exportActions(getApplication(), actions, from, to)
                _generatedFile.value = Result.success(file)
            } catch (e: Exception) {
                _generatedFile.value = Result.failure(e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearGeneratedFile() {
        _generatedFile.value = null
    }
}

data class PeriodStats(
    val batchCount: Int,
    val totalInitialQty: Double,
    val totalCurrentQty: Double,
    val actionCount: Int,
    val transferCount: Int
) {
    val totalLoss: Double get() = totalInitialQty - totalCurrentQty
    val lossPercent: Double get() = if (totalInitialQty > 0)
        totalLoss / totalInitialQty * 100 else 0.0
}
