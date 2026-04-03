package com.taqnid.batch.data.model

import java.util.Date

/**
 * Types d'actions traçables sur un lot.
 */
enum class ActionType(val labelFr: String) {
    PESEE("Pesée"),
    PERTE("Perte"),
    AJOUT_EAU("Ajout eau"),
    AJOUT_NUTRIMENT("Ajout nutriment"),
    TRAITEMENT("Traitement"),
    CHANGEMENT_STADE("Changement de stade"),
    TRANSFERT_ENTRANT("Transfert entrant"),
    TRANSFERT_SORTANT("Transfert sortant"),
    RETRAIT_TEST("Retrait pour test qualité"),
    RETRAIT_DESTRUCTION("Retrait pour destruction"),
    RETRAIT_VENTE("Retrait pour vente"),
    NOTE("Note / Observation"),
    AJUSTEMENT_INVENTAIRE("Ajustement inventaire")
}

/**
 * Action enregistrée sur un lot — constitue la timeline de traçabilité.
 */
data class BatchAction(
    val id: String = "",
    val batchId: String = "",
    val taqninId: String = "",           // Dupliqué pour lisibilité
    val actionType: ActionType = ActionType.NOTE,
    val performedBy: String = "",        // UID de l'utilisateur
    val performedByName: String = "",
    val timestamp: Date = Date(),
    val quantityBefore: Double? = null,  // Quantité avant action
    val quantityAfter: Double? = null,   // Quantité après action
    val quantityDelta: Double? = null,   // Variation (négatif = perte)
    val unit: String = "g",
    val stageFrom: BatchStage? = null,   // Stade source (si changement de stade)
    val stageTo: BatchStage? = null,     // Stade destination
    val description: String = "",
    val locationId: String = "",
    val isSynced: Boolean = false
)
