package com.taqnid.batch.domain

import com.taqnid.batch.data.model.ActionType
import com.taqnid.batch.data.model.BatchStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRulesTest {

    private fun ok(check: BatchRules.Check) = check == BatchRules.Check.Ok

    @Test
    fun `unites controlees`() {
        assertTrue(ok(BatchRules.checkUnit("g")))
        assertTrue(ok(BatchRules.checkUnit("kg")))
        assertFalse(ok(BatchRules.checkUnit("tonnes")))
        assertFalse(ok(BatchRules.checkUnit("")))
    }

    @Test
    fun `quantite initiale`() {
        assertTrue(ok(BatchRules.checkInitialQuantity(1.5)))
        for (bad in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 2e9)) {
            assertFalse(bad.toString(), ok(BatchRules.checkInitialQuantity(bad)))
        }
    }

    @Test
    fun `jamais sous zero`() {
        assertFalse(ok(BatchRules.checkNewQuantity(100.0, -1.0, ActionType.PESEE)))
        assertFalse(ok(BatchRules.checkNewQuantity(100.0, null, ActionType.PESEE)))
    }

    @Test
    fun `une perte ou un retrait ne peut qu'enlever`() {
        for (action in listOf(ActionType.PERTE, ActionType.RETRAIT_VENTE, ActionType.RETRAIT_DESTRUCTION,
                ActionType.RETRAIT_TEST, ActionType.TRANSFERT_SORTANT)) {
            assertTrue(ok(BatchRules.checkNewQuantity(100.0, 80.0, action)))
            assertFalse(action.name, ok(BatchRules.checkNewQuantity(100.0, 120.0, action)))
        }
        // Une pesée peut constater une hausse (humidité, reprise de stock)
        assertTrue(ok(BatchRules.checkNewQuantity(100.0, 120.0, ActionType.PESEE)))
    }

    @Test
    fun `transitions de stade en avant seulement`() {
        assertTrue(BatchRules.canMove(BatchStage.GERMINATION, BatchStage.VEGETATION))
        assertTrue(BatchRules.canMove(BatchStage.GERMINATION, BatchStage.RECOLTE))
        assertFalse(BatchRules.canMove(BatchStage.FLORAISON, BatchStage.VEGETATION))
        assertFalse(BatchRules.canMove(BatchStage.SECHAGE, BatchStage.SECHAGE))
    }

    @Test
    fun `destruction et retrait toujours possibles, rien apres un etat final`() {
        assertTrue(BatchRules.canMove(BatchStage.FLORAISON, BatchStage.DETRUIT))
        assertTrue(BatchRules.canMove(BatchStage.EMBALLAGE, BatchStage.RETIRE))
        for (terminal in listOf(BatchStage.VENTE, BatchStage.DETRUIT, BatchStage.RETIRE)) {
            assertEquals(emptyList<BatchStage>(), BatchRules.allowedNextStages(terminal))
        }
    }

    @Test
    fun `un lot vide ou termine ne part pas en transfert`() {
        assertTrue(ok(BatchRules.checkTransferable(BatchStage.EMBALLAGE, 10.0)))
        assertFalse(ok(BatchRules.checkTransferable(BatchStage.EMBALLAGE, 0.0)))
        assertFalse(ok(BatchRules.checkTransferable(BatchStage.DETRUIT, 10.0)))
    }
}
