package com.taqnid.batch.utils

import android.content.Context
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.BatchAction
import com.taqnid.batch.data.model.Transfer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exporteur CSV pour les rapports réglementaires.
 */
object CsvExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    fun exportBatches(context: Context, batches: List<Batch>, fromDate: Date, toDate: Date): File {
        val fileName = "lots_${dateOnlyFormat.format(fromDate)}_${dateOnlyFormat.format(toDate)}.csv"
        val file = getOutputFile(context, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            // BOM UTF-8 pour Excel
            writer.write("\uFEFF")
            writer.write("Taqnin ID;Variété;Type;Stade;Statut conformité;Qté initiale;Qté actuelle;Unité;Emplacement;Propriétaire;Licence;Date début;Dernière MAJ;Date expiration;Notes\n")
            batches.forEach { b ->
                writer.write(listOf(
                    b.taqninId, b.variety, b.type.labelFr, b.currentStage.labelFr,
                    b.complianceStatus.labelFr, "%.2f".format(b.initialQuantity),
                    "%.2f".format(b.currentQuantity), b.unit, b.locationName,
                    b.ownerName, b.licenseNumber, dateFormat.format(b.startDate),
                    dateFormat.format(b.lastUpdated), b.expirationDate?.let { dateFormat.format(it) } ?: "",
                    b.notes.replace(";", ",")
                ).joinToString(";") + "\n")
            }
        }
        return file
    }

    fun exportActions(context: Context, actions: List<BatchAction>, fromDate: Date, toDate: Date): File {
        val fileName = "actions_${dateOnlyFormat.format(fromDate)}_${dateOnlyFormat.format(toDate)}.csv"
        val file = getOutputFile(context, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("Date;Taqnin ID;Type action;Réalisé par;Qté avant;Qté après;Variation;Unité;Stade de;Stade vers;Description\n")
            actions.sortedByDescending { it.timestamp }.forEach { a ->
                writer.write(listOf(
                    dateFormat.format(a.timestamp), a.taqninId, a.actionType.labelFr,
                    a.performedByName, a.quantityBefore?.let { "%.2f".format(it) } ?: "",
                    a.quantityAfter?.let { "%.2f".format(it) } ?: "",
                    a.quantityDelta?.let { "%.2f".format(it) } ?: "", a.unit,
                    a.stageFrom?.labelFr ?: "", a.stageTo?.labelFr ?: "",
                    a.description.replace(";", ",")
                ).joinToString(";") + "\n")
            }
        }
        return file
    }

    fun exportTransfers(context: Context, transfers: List<Transfer>, fromDate: Date, toDate: Date): File {
        val fileName = "transferts_${dateOnlyFormat.format(fromDate)}_${dateOnlyFormat.format(toDate)}.csv"
        val file = getOutputFile(context, fileName)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("N° Transfert;Statut;Expéditeur;ID Expéditeur;Destinataire;ID Destinataire;Conducteur;Véhicule;Date création;Date expédition;Date réception;Nombre de lots;Créé par\n")
            transfers.forEach { t ->
                writer.write(listOf(
                    t.transferNumber, t.status.labelFr, t.originLicenseName, t.originLicenseId,
                    t.destinationLicenseName, t.destinationLicenseId, t.driverName, t.vehiclePlate,
                    dateFormat.format(t.createdAt), t.dispatchedAt?.let { dateFormat.format(it) } ?: "",
                    t.receivedAt?.let { dateFormat.format(it) } ?: "", t.batchIds.size.toString(),
                    t.createdByName
                ).joinToString(";") + "\n")
            }
        }
        return file
    }

    private fun getOutputFile(context: Context, fileName: String): File {
        val dir = context.getExternalFilesDir("exports") ?: context.filesDir
        dir.mkdirs()
        return File(dir, fileName)
    }
}
