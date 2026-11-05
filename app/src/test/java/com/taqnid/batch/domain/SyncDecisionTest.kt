package com.taqnid.batch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDecisionTest {

    @Test
    fun `absent du serveur - creation`() {
        assertEquals(SyncDecision.Create, SyncDecision.decide(baseVersion = 0, serverVersion = null, sameContent = false))
    }

    @Test
    fun `serveur reste a la version de depart - version suivante`() {
        assertEquals(SyncDecision.Update(4), SyncDecision.decide(baseVersion = 3, serverVersion = 3, sameContent = false))
    }

    @Test
    fun `reponse perdue - le serveur a deja cet etat, rien n'est reecrit`() {
        assertEquals(SyncDecision.AlreadyApplied(4), SyncDecision.decide(baseVersion = 3, serverVersion = 4, sameContent = true))
    }

    @Test
    fun `modification concurrente - conflit, jamais d'ecrasement`() {
        val decision = SyncDecision.decide(baseVersion = 3, serverVersion = 5, sameContent = false)
        assertTrue(decision is SyncDecision.Conflict)
        assertEquals(5L, (decision as SyncDecision.Conflict).serverVersion)
    }

    @Test
    fun `lot ne sur l'appareil mais deja present ailleurs avec un autre contenu - conflit`() {
        assertTrue(SyncDecision.decide(baseVersion = 0, serverVersion = 1, sameContent = false) is SyncDecision.Conflict)
    }
}
