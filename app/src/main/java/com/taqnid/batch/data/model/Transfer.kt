package com.taqnid.batch.data.model

import java.util.Date

/**
 * Statuts possibles d'un manifeste de transfert.
 */
enum class TransferStatus(val labelFr: String) {
    BROUILLON("Brouillon"),
    EN_TRANSIT("En transit"),
    RECEPTIONNE("Réceptionné"),
    REJETE("Rejeté"),
    ANNULE("Annulé")
}

/**
 * Manifeste de transfert entre deux entités licenciées.
 * Regroupe un ou plusieurs lots.
 */
data class Transfer(
    val id: String = "",
    val transferNumber: String = "",       // Ex: TRF-2024-0001
    val status: TransferStatus = TransferStatus.BROUILLON,
    val originLicenseId: String = "",      // Licencié expéditeur
    val originLicenseName: String = "",
    val destinationLicenseId: String = "", // Licencié destinataire
    val destinationLicenseName: String = "",
    val batchIds: List<String> = emptyList(), // Liste des Taqnin IDs inclus
    val batchSummaries: List<TransferBatchItem> = emptyList(),
    val driverName: String = "",
    val vehiclePlate: String = "",
    val qrCodeContent: String = "",        // Contenu encodé dans le QR de transport
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Date = Date(),
    val dispatchedAt: Date? = null,
    val receivedAt: Date? = null,
    val receivedBy: String = "",
    val notes: String = "",
    val isSynced: Boolean = false
)

/**
 * Résumé d'un lot inclus dans un manifeste de transfert.
 */
data class TransferBatchItem(
    val taqninId: String = "",
    val variety: String = "",
    val stage: BatchStage = BatchStage.EMBALLAGE,
    val quantity: Double = 0.0,
    val unit: String = "g",
    val quantityReceived: Double? = null,  // Renseigné à la réception
    val discrepancy: Double? = null        // Écart constaté
)
