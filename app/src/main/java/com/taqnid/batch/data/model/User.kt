package com.taqnid.batch.data.model

/**
 * Rôles utilisateur avec niveaux de permission croissants.
 */
enum class UserRole(val labelFr: String, val level: Int) {
    LECTEUR("Lecteur", 0),         // Consultation uniquement
    OUVRIER("Ouvrier", 1),         // Scan et pesées
    MANAGER("Manager", 2),         // Création et transferts
    ADMIN("Administrateur", 3)     // Tous droits
}

/**
 * Modèle utilisateur de l'application.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.LECTEUR,
    val licenseId: String = "",        // Licence de l'entité rattachée
    val licenseName: String = "",
    val isActive: Boolean = true,
    val biometricEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    fun canCreate() = role.level >= UserRole.MANAGER.level
    fun canTransfer() = role.level >= UserRole.MANAGER.level
    fun canScan() = role.level >= UserRole.OUVRIER.level
    fun canAdmin() = role.level >= UserRole.ADMIN.level
    fun canExport() = role.level >= UserRole.MANAGER.level
}
