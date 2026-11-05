package com.taqnid.batch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    @Test
    fun `identifiant provisoire seul`() {
        assertEquals(QrPayload.BatchId("TAQ-2026-7K3M9QX2PD", official = false), QrPayload.parse("TAQ-2026-7K3M9QX2PD"))
    }

    @Test
    fun `espaces et minuscules du lecteur sont toleres`() {
        assertEquals(QrPayload.BatchId("TAQ-2026-7K3M9QX2PD", official = false), QrPayload.parse("  taq-2026-7k3m9qx2pd \n"))
    }

    @Test
    fun `etiquette web - adresse de verification publique`() {
        assertEquals(
            QrPayload.BatchId("MA-RIF-AKN01-BELD-2026-0001", official = true),
            QrPayload.parse("https://taqnin.example/verify?id=MA-RIF-AKN01-BELD-2026-0001")
        )
        assertEquals(
            QrPayload.BatchId("MA-RIF-AKN01-BELD-2026-0001", official = true),
            QrPayload.parse("https://taqnin.example/verify?lang=fr&id=MA-RIF-AKN01-BELD-2026-0001#haut")
        )
    }

    @Test
    fun `QR JSON de la plateforme web`() {
        assertEquals(
            QrPayload.BatchId("MA-RIF-AKN01-BELD-2026-0001", official = true),
            QrPayload.parse("""{"id":"MA-RIF-AKN01-BELD-2026-0001","bid":1,"corridor":"C2","thc":0.8}""")
        )
    }

    @Test
    fun `manifeste de transfert`() {
        val parsed = QrPayload.parse("TRF:TRF-2026-AB12CD34|FROM:ORG-A|TO:ORG-B|LOTS:TAQ-2026-7K3M9QX2PD,MA-RIF-AKN01-BELD-2026-0001")
        assertEquals(
            QrPayload.TransferManifest("TRF-2026-AB12CD34", listOf("TAQ-2026-7K3M9QX2PD", "MA-RIF-AKN01-BELD-2026-0001")),
            parsed
        )
    }

    @Test
    fun `ancien numero de transfert encore lisible`() {
        assertTrue(QrPayload.parse("TRF:TRF-2024-01234|FROM:A|TO:B|LOTS:TAQ-A3F2-2024") is QrPayload.TransferManifest)
    }

    @Test
    fun `codes invalides`() {
        for (raw in listOf(null, "", "   ", "https://exemple.com/", "https://exemple.com/?id=", "{\"nom\":\"x\"}",
                "1234567890123", "TRF:pas-un-numero", "TRF:TRF-2026-AB12CD34|LOTS:<script>", "x".repeat(3000))) {
            assertTrue(raw?.take(30) ?: "null", QrPayload.parse(raw) is QrPayload.Invalid)
        }
    }
}
