package com.taqnid.batch.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.taqnid.batch.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@Config(application = android.app.Application::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class ExportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val batch = Batch(
        id = "b1", taqninId = "TAQ-2026-7K3M9QX2PD", variety = "Beldiya; locale\nRif",
        initialQuantity = 1000.0, currentQuantity = 950.0, unit = "g", locationName = "Serre 1",
        ownerName = "=HYPERLINK(\"http://x\")", licenseNumber = "ORG-A", notes = "+cmd|' /C calc'!A0",
    )

    @Test
    fun `export CSV des lots - fichier cree, colonnes intactes, formules neutralisees`() {
        val file = CsvExporter.exportBatches(context, listOf(batch), Date(0), Date())
        assertTrue(file.exists())
        assertFalse("pas de sous-dossier dans le nom", file.name.contains('/'))
        val lines = file.readText(Charsets.UTF_8).removePrefix("﻿").trimEnd('\n').split('\n')
        assertEquals("en-tête + une ligne, malgré le saut de ligne saisi", 2, lines.size)
        val cells = lines[1].split(';')
        assertEquals(lines[0].split(';').size, cells.size)
        assertTrue(cells.any { it.startsWith("'=HYPERLINK") })
        assertTrue(cells.any { it.startsWith("'+cmd") })
    }

    @Test
    fun `export CSV des transferts et des actions`() {
        val transfer = Transfer(id = "t1", transferNumber = "TRF-2026-AB12CD34", originLicenseName = "-1+1",
            batchIds = listOf(batch.taqninId), createdAt = Date())
        val tFile = CsvExporter.exportTransfers(context, listOf(transfer), Date(0), Date())
        assertTrue(tFile.readText().contains("'-1+1"))
        val action = BatchAction(id = "a1", batchId = "b1", taqninId = batch.taqninId, quantityDelta = -50.0,
            description = "@SUM(1)", timestamp = Date())
        val aFile = CsvExporter.exportActions(context, listOf(action), Date(0), Date())
        val text = aFile.readText()
        assertTrue(text.contains("'@SUM(1)"))
        // Une variation négative reste un nombre
        assertTrue(text.contains(";-50"))
    }

    @Test
    fun `manifeste de transfert PDF`() {
        val transfer = Transfer(
            id = "t2", transferNumber = "TRF-2026-ZX98YW76", status = TransferStatus.EN_TRANSIT,
            originLicenseId = "ORG-A", originLicenseName = "Coop A", destinationLicenseId = "ORG-B",
            destinationLicenseName = "Coop B", batchIds = listOf(batch.taqninId),
            batchSummaries = listOf(TransferBatchItem(batch.taqninId, "Beldiya", BatchStage.EMBALLAGE, 950.0, "g")),
            driverName = "Chauffeur", vehiclePlate = "1-A-1", qrCodeContent = "TRF:TRF-2026-ZX98YW76", createdAt = Date(),
        )
        val file = PdfExporter.exportTransferManifest(context, transfer)
        assertTrue(file.length() > 500)
        assertEquals("%PDF", file.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
    }
}
