package com.taqnid.batch.domain

import com.taqnid.batch.data.model.ActionType
import com.taqnid.batch.data.model.BatchStage

/**
 * Règles métier des lots, vérifiées avant toute écriture locale.
 * Les règles Firestore refont les contrôles essentiels côté serveur.
 */
object BatchRules {

    /** Unités acceptées, identiques à celles des règles Firestore. */
    val UNITS = listOf("g", "kg", "unités", "plants", "mL", "L")

    /** Plafond de saisie : au-delà, c'est une erreur de frappe (1 000 tonnes en grammes). */
    const val MAX_QUANTITY = 1_000_000_000.0

    sealed class Check {
        object Ok : Check()
        data class Refused(val message: String) : Check()
    }

    fun checkUnit(unit: String): Check =
        if (unit in UNITS) Check.Ok
        else Check.Refused("Unité « $unit » non reconnue : ${UNITS.joinToString(", ")}")

    /** Quantité initiale d'un nouveau lot. */
    fun checkInitialQuantity(value: Double?): Check = when {
        value == null || value.isNaN() || value.isInfinite() -> Check.Refused("Quantité invalide")
        value <= 0.0 -> Check.Refused("La quantité initiale doit être positive")
        value > MAX_QUANTITY -> Check.Refused("Quantité trop élevée : vérifier l'unité")
        else -> Check.Ok
    }

    /**
     * Nouvelle quantité après une action. Une perte, un retrait ou un transfert
     * sortant ne peuvent qu'enlever ; aucune action ne fait passer sous zéro.
     */
    fun checkNewQuantity(current: Double, newValue: Double?, action: ActionType): Check {
        if (newValue == null || newValue.isNaN() || newValue.isInfinite()) return Check.Refused("Quantité invalide")
        if (newValue < 0.0) return Check.Refused("La quantité ne peut pas être négative")
        if (newValue > MAX_QUANTITY) return Check.Refused("Quantité trop élevée : vérifier l'unité")
        val decreasing = action in setOf(
            ActionType.PERTE,
            ActionType.RETRAIT_TEST,
            ActionType.RETRAIT_DESTRUCTION,
            ActionType.RETRAIT_VENTE,
            ActionType.TRANSFERT_SORTANT,
        )
        if (decreasing && newValue > current) {
            return Check.Refused("Une ${action.labelFr.lowercase()} ne peut pas augmenter la quantité")
        }
        return Check.Ok
    }

    private val ORDER = listOf(
        BatchStage.GERMINATION,
        BatchStage.VEGETATION,
        BatchStage.FLORAISON,
        BatchStage.RECOLTE,
        BatchStage.SECHAGE,
        BatchStage.CURING,
        BatchStage.TRANSFORMATION,
        BatchStage.EMBALLAGE,
        BatchStage.VENTE,
    )

    private val TERMINAL = setOf(BatchStage.VENTE, BatchStage.DETRUIT, BatchStage.RETIRE)

    fun isTerminal(stage: BatchStage) = stage in TERMINAL

    /**
     * Transitions permises : en avant dans le cycle (une étape peut être
     * sautée, par exemple un lot acheté sec), jamais en arrière ; destruction
     * ou retrait à tout moment ; plus rien après la vente, la destruction ou
     * le retrait.
     */
    fun canMove(from: BatchStage, to: BatchStage): Boolean {
        if (from == to || from in TERMINAL) return false
        if (to == BatchStage.DETRUIT || to == BatchStage.RETIRE) return true
        return ORDER.indexOf(to) > ORDER.indexOf(from)
    }

    fun allowedNextStages(from: BatchStage): List<BatchStage> =
        BatchStage.values().filter { canMove(from, it) }

    /** Un lot peut partir en transfert s'il est actif et non vide. */
    fun checkTransferable(stage: BatchStage, quantity: Double): Check = when {
        stage in TERMINAL -> Check.Refused("Lot ${stage.labelFr.lowercase()} : il ne peut plus être transféré")
        quantity <= 0.0 -> Check.Refused("Lot vide : rien à transférer")
        else -> Check.Ok
    }
}
