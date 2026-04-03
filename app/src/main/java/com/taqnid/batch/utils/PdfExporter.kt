package com.taqnid.batch.utils

import android.content.Context
import android.os.Environment
import com.itextpdf.text.*
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.BatchAction
import com.taqnid.batch.data.model.Transfer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exporteur PDF utilisant iTextG.
 * Génère des rapports réglementaires de traçabilité.
 */
object PdfExporter {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    // Couleurs de la charte Taqnin
    private val colorPrimary = BaseColor(0x1B, 0x5E, 0x20)   // Vert foncé
    private val colorHeader = BaseColor(0x2E, 0x7D, 0x32)    // Vert moyen
    private val colorLight = BaseColor(0xE8, 0xF5, 0xE9)     // Vert très clair

    /**
     * Exporte un rapport de mouvement de lots sur une période.
     */
    fun exportBatchReport(
        context: Context,
        batches: List<Batch>,
        actions: List<BatchAction>,
        fromDate: Date,
        toDate: Date
    ): File {
        val fileName = "rapport_lots_${dateOnlyFormat.format(fromDate)}_${dateOnlyFormat.format(toDate)}.pdf"
        val file = getOutputFile(context, fileName)

        val document = Document(PageSize.A4, 40f, 40f, 60f, 40f)
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()

        // En-tête
        addTitle(document, "Rapport de Mouvements des Lots")
        addSubtitle(document, "Période : ${dateOnlyFormat.format(fromDate)} → ${dateOnlyFormat.format(toDate)}")
        addSubtitle(document, "Généré le : ${dateFormat.format(Date())}")
        document.add(Chunk.NEWLINE)

        // Résumé statistique
        addSectionTitle(document, "Résumé")
        val totalInitial = batches.sumOf { it.initialQuantity }
        val totalCurrent = batches.sumOf { it.currentQuantity }
        val totalLoss = totalInitial - totalCurrent
        addStatRow(document, "Nombre de lots", "${batches.size}")
        addStatRow(document, "Quantité totale initiale", "${"%.2f".format(totalInitial)} g")
        addStatRow(document, "Quantité totale actuelle", "${"%.2f".format(totalCurrent)} g")
        addStatRow(document, "Pertes totales", "${"%.2f".format(totalLoss)} g")
        document.add(Chunk.NEWLINE)

        // Tableau des lots
        addSectionTitle(document, "Détail des lots")
        val batchTable = PdfPTable(6)
        batchTable.widthPercentage = 100f
        batchTable.setWidths(floatArrayOf(2f, 2.5f, 1.5f, 1.5f, 1.5f, 2f))
        addTableHeader(batchTable, listOf("Taqnin ID", "Variété", "Stade", "Qté initiale", "Qté actuelle", "Statut"))
        batches.forEach { batch ->
            addTableRow(batchTable, listOf(
                batch.taqninId,
                batch.variety,
                batch.currentStage.labelFr,
                "${"%.2f".format(batch.initialQuantity)} ${batch.unit}",
                "${"%.2f".format(batch.currentQuantity)} ${batch.unit}",
                batch.complianceStatus.labelFr
            ))
        }
        document.add(batchTable)
        document.add(Chunk.NEWLINE)

        // Tableau des actions
        if (actions.isNotEmpty()) {
            addSectionTitle(document, "Historique des actions (${actions.size})")
            val actionTable = PdfPTable(5)
            actionTable.widthPercentage = 100f
            actionTable.setWidths(floatArrayOf(2f, 1.5f, 2f, 1.5f, 3f))
            addTableHeader(actionTable, listOf("Date", "Taqnin ID", "Type d'action", "Variation", "Description"))
            actions.sortedByDescending { it.timestamp }.forEach { action ->
                val delta = action.quantityDelta?.let { "${"%.2f".format(it)} ${action.unit}" } ?: "-"
                addTableRow(actionTable, listOf(
                    dateFormat.format(action.timestamp),
                    action.taqninId,
                    action.actionType.labelFr,
                    delta,
                    action.description.take(60)
                ))
            }
            document.add(actionTable)
        }

        document.close()
        return file
    }

    /**
     * Exporte un rapport de transferts sur une période.
     */
    fun exportTransferReport(
        context: Context,
        transfers: List<Transfer>,
        fromDate: Date,
        toDate: Date
    ): File {
        val fileName = "rapport_transferts_${dateOnlyFormat.format(fromDate)}_${dateOnlyFormat.format(toDate)}.pdf"
        val file = getOutputFile(context, fileName)

        val document = Document(PageSize.A4.rotate(), 40f, 40f, 60f, 40f)
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()

        addTitle(document, "Rapport de Transferts")
        addSubtitle(document, "Période : ${dateOnlyFormat.format(fromDate)} → ${dateOnlyFormat.format(toDate)}")
        document.add(Chunk.NEWLINE)

        val table = PdfPTable(7)
        table.widthPercentage = 100f
        addTableHeader(table, listOf("N° Transfert", "Date", "Expéditeur", "Destinataire", "Lots", "Statut", "Conducteur"))
        transfers.forEach { t ->
            addTableRow(table, listOf(
                t.transferNumber,
                dateFormat.format(t.createdAt),
                t.originLicenseName,
                t.destinationLicenseName,
                t.batchIds.size.toString(),
                t.status.labelFr,
                t.driverName
            ))
        }
        document.add(table)
        document.close()
        return file
    }

    /**
     * Exporte un manifeste de transfert individuel (pour le chauffeur).
     */
    fun exportTransferManifest(context: Context, transfer: Transfer): File {
        val fileName = "manifeste_${transfer.transferNumber}.pdf"
        val file = getOutputFile(context, fileName)

        val document = Document(PageSize.A4, 40f, 40f, 60f, 40f)
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()

        addTitle(document, "MANIFESTE DE TRANSFERT")
        addSubtitle(document, transfer.transferNumber)
        document.add(Chunk.NEWLINE)

        addStatRow(document, "Expéditeur", "${transfer.originLicenseName} (${transfer.originLicenseId})")
        addStatRow(document, "Destinataire", "${transfer.destinationLicenseName} (${transfer.destinationLicenseId})")
        addStatRow(document, "Conducteur", transfer.driverName)
        addStatRow(document, "Véhicule", transfer.vehiclePlate)
        addStatRow(document, "Date d'expédition", transfer.dispatchedAt?.let { dateFormat.format(it) } ?: "-")
        document.add(Chunk.NEWLINE)

        addSectionTitle(document, "Lots transportés")
        val table = PdfPTable(4)
        table.widthPercentage = 100f
        addTableHeader(table, listOf("Taqnin ID", "Variété", "Stade", "Quantité"))
        transfer.batchSummaries.forEach { item ->
            addTableRow(table, listOf(
                item.taqninId,
                item.variety,
                item.stage.labelFr,
                "${"%.2f".format(item.quantity)} ${item.unit}"
            ))
        }
        document.add(table)

        if (transfer.notes.isNotBlank()) {
            document.add(Chunk.NEWLINE)
            addSectionTitle(document, "Notes")
            document.add(Paragraph(transfer.notes, Font(Font.FontFamily.HELVETICA, 10f)))
        }

        document.close()
        return file
    }

    // ── Helpers PDF ───────────────────────────────────────────────────────────

    private fun addTitle(doc: Document, text: String) {
        val font = Font(Font.FontFamily.HELVETICA, 18f, Font.BOLD, colorPrimary)
        doc.add(Paragraph(text, font).also { it.spacingAfter = 6f })
    }

    private fun addSubtitle(doc: Document, text: String) {
        val font = Font(Font.FontFamily.HELVETICA, 11f, Font.NORMAL, BaseColor.DARK_GRAY)
        doc.add(Paragraph(text, font).also { it.spacingAfter = 2f })
    }

    private fun addSectionTitle(doc: Document, text: String) {
        val font = Font(Font.FontFamily.HELVETICA, 13f, Font.BOLD, colorHeader)
        doc.add(Paragraph(text, font).also { it.spacingBefore = 10f; it.spacingAfter = 4f })
    }

    private fun addStatRow(doc: Document, label: String, value: String) {
        val labelFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD)
        val valueFont = Font(Font.FontFamily.HELVETICA, 10f)
        val p = Paragraph()
        p.add(Chunk("$label : ", labelFont))
        p.add(Chunk(value, valueFont))
        p.spacingAfter = 2f
        doc.add(p)
    }

    private fun addTableHeader(table: PdfPTable, headers: List<String>) {
        val font = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD, BaseColor.WHITE)
        headers.forEach { header ->
            val cell = PdfPCell(Phrase(header, font))
            cell.backgroundColor = colorHeader
            cell.horizontalAlignment = Element.ALIGN_CENTER
            cell.paddingBottom = 5f
            table.addCell(cell)
        }
    }

    private fun addTableRow(table: PdfPTable, values: List<String>, alternate: Boolean = false) {
        val font = Font(Font.FontFamily.HELVETICA, 8f)
        values.forEach { value ->
            val cell = PdfPCell(Phrase(value, font))
            cell.paddingBottom = 4f
            cell.paddingLeft = 4f
            table.addCell(cell)
        }
    }

    private fun getOutputFile(context: Context, fileName: String): File {
        val dir = context.getExternalFilesDir("exports") ?: context.filesDir
        dir.mkdirs()
        return File(dir, fileName)
    }
}
