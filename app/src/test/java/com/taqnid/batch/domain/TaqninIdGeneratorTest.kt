package com.taqnid.batch.domain

import com.taqnid.batch.utils.TaqninIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaqninIdGeneratorTest {

    @Test
    fun `un identifiant provisoire suit le format TAQ-annee-10 caracteres`() {
        val id = TaqninIdGenerator.generate(2026)
        assertTrue(id, Regex("^TAQ-2026-[0-9A-HJKMNP-TV-Z]{10}$").matches(id))
        assertTrue(TaqninIdGenerator.isProvisional(id))
        assertEquals(2026, TaqninIdGenerator.extractYear(id))
    }

    @Test
    fun `aucune collision sur 200 000 identifiants`() {
        val ids = HashSet<String>()
        repeat(200_000) { assertTrue("doublon", ids.add(TaqninIdGenerator.generate(2026))) }
    }

    @Test
    fun `les caracteres ambigus I L O U ne sont jamais produits`() {
        repeat(10_000) {
            val suffix = TaqninIdGenerator.generate(2026).substringAfterLast('-')
            assertFalse(suffix, suffix.any { it in "ILOU" })
        }
    }

    @Test
    fun `l'identifiant officiel de la plateforme web est reconnu`() {
        assertTrue(TaqninIdGenerator.isWebId("MA-RIF-AKN01-BELD-2026-0001"))
        assertTrue(TaqninIdGenerator.isValid("MA-RIF-AKN01-BELD-2026-0001"))
        assertEquals(2026, TaqninIdGenerator.extractYear("MA-RIF-AKN01-BELD-2026-0001"))
    }

    @Test
    fun `l'ancien format reste lisible`() {
        assertTrue(TaqninIdGenerator.isLegacy("TAQ-A3F2-2024"))
        assertTrue(TaqninIdGenerator.isValid("TAQ-A3F2-2024"))
        assertEquals(2024, TaqninIdGenerator.extractYear("TAQ-A3F2-2024"))
    }

    @Test
    fun `les identifiants malformes sont refuses`() {
        for (bad in listOf("", "TAQ", "TAQ-2026-ABC", "TAQ-2026-ILOUILOUIL", "taq-2026-7k3m9qx2pd",
                "MA-RIF-AKN01-BELD-26-0001", "MA-RIF-AKN01-BELD-2026-1", "<script>", "TAQ-2026-7K3M9QX2PD-X")) {
            assertFalse(bad, TaqninIdGenerator.isValid(bad))
        }
        assertNull(TaqninIdGenerator.extractYear("n'importe quoi"))
    }

    @Test
    fun `un numero de transfert est unique et lisible`() {
        val numbers = HashSet<String>()
        repeat(50_000) {
            val n = TaqninIdGenerator.generateTransferNumber(2026)
            assertTrue(n, Regex("^TRF-2026-[0-9A-Z]{8}$").matches(n))
            assertTrue("doublon", numbers.add(n))
        }
    }
}
