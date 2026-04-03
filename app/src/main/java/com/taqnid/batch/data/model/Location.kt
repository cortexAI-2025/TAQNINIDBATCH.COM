package com.taqnid.batch.data.model

/**
 * Types d'emplacements dans la chaîne de production.
 */
enum class LocationType(val labelFr: String) {
    CHAMBRE_GERMINATION("Chambre de germination"),
    CHAMBRE_VEGETATION("Chambre de végétation"),
    CHAMBRE_FLORAISON("Chambre de floraison"),
    SALLE_SECHAGE("Salle de séchage"),
    SALLE_CURING("Salle de curing"),
    LABORATOIRE("Laboratoire"),
    ENTREPOT("Entrepôt"),
    MAGASIN("Magasin"),
    QUAI_EXPEDITION("Quai d'expédition"),
    ZONE_DESTRUCTION("Zone de destruction"),
    EXTERNE("Externe / En transit")
}

/**
 * Emplacement physique ou logique dans le système de traçabilité.
 */
data class Location(
    val id: String = "",
    val name: String = "",
    val type: LocationType = LocationType.ENTREPOT,
    val licenseId: String = "",        // Entité propriétaire
    val licenseName: String = "",
    val capacity: Double? = null,      // Capacité maximale (grammes)
    val currentLoad: Double = 0.0,     // Charge actuelle
    val isActive: Boolean = true,
    val description: String = ""
) {
    fun occupancyPercent(): Float = capacity?.let {
        if (it > 0) (currentLoad / it * 100f).toFloat() else 0f
    } ?: 0f
}
