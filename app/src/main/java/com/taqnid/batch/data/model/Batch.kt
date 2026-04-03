package com.taqnid.batch.data.model

import java.util.Date

/**
 * Énumération des stades de vie d'un lot (seed to sale).
 */
enum class BatchStage(val labelFr: String) {
    GERMINATION("Germination"),
    VEGETATION("Végétation"),
    FLORAISON("Floraison"),
    RECOLTE("Récolte"),
    SECHAGE("Séchage"),
    CURING("Curing"),
    TRANSFORMATION("Transformation"),
    EMBALLAGE("Emballage"),
    VENTE("Vente"),
    DETRUIT("Détruit"),
    RETIRE("Retiré")
}

/**
 * Statut de conformité d'un lot.
 */
enum class ComplianceStatus(val labelFr: String) {
    CONFORME("Conforme"),          // Vert
    ATTENTION("Attention"),        // Orange
    NON_CONFORME("Non conforme"),  // Rouge
    EN_ATTENTE("En attente")       // Gris
}

/**
 * Type de matière première d'un lot.
 */
enum class BatchType(val labelFr: String) {
    GRAINE("Graine"),
    CLONE("Clone"),
    BOUTURE("Bouture"),
    PRODUIT_FINI("Produit fini"),
    EXTRAIT("Extrait"),
    HUILE("Huile")
}

/**
 * Modèle métier principal représentant un lot de traçabilité.
 * Identifié par un Taqnin ID unique au format TAQ-XXXX-YYYY.
 */
data class Batch(
    val id: String = "",
    val taqninId: String = "",          // Ex: TAQ-A3F2-2024
    val variety: String = "",           // Variété (ex: OG Kush, Blue Dream…)
    val type: BatchType = BatchType.GRAINE,
    val initialQuantity: Double = 0.0,  // Quantité initiale (grammes ou unités)
    val currentQuantity: Double = 0.0,  // Quantité actuelle
    val unit: String = "g",             // Unité de mesure
    val locationId: String = "",        // Emplacement courant
    val locationName: String = "",      // Nom lisible de l'emplacement
    val currentStage: BatchStage = BatchStage.GERMINATION,
    val complianceStatus: ComplianceStatus = ComplianceStatus.EN_ATTENTE,
    val ownerId: String = "",           // UID Firebase de l'utilisateur responsable
    val ownerName: String = "",
    val licenseNumber: String = "",     // Numéro de licence du licencié propriétaire
    val startDate: Date = Date(),
    val lastUpdated: Date = Date(),
    val expirationDate: Date? = null,   // Pour les produits transformés
    val notes: String = "",
    val isSynced: Boolean = false       // État de synchronisation Firebase
)
