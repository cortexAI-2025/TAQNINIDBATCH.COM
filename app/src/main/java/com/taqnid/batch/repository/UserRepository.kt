package com.taqnid.batch.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.taqnid.batch.data.model.User
import com.taqnid.batch.data.model.UserRole
import com.taqnid.batch.data.remote.FirebaseRepository
import kotlinx.coroutines.tasks.await

/**
 * Repository utilisateur — gère l'authentification Firebase et le profil local.
 */
class UserRepository(
    context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firebaseRepo: FirebaseRepository = FirebaseRepository()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("taqnin_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_LICENSE_NAME = "license_name"
        private const val KEY_BIOMETRIC = "biometric_enabled"
    }

    fun getCurrentFirebaseUser() = firebaseAuth.currentUser

    fun isLoggedIn() = firebaseAuth.currentUser != null

    /**
     * Connexion par email / mot de passe.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID introuvable")
            val user = firebaseRepo.getUser(uid) ?: User(
                uid = uid,
                email = email,
                displayName = result.user?.displayName ?: email.substringBefore("@"),
                role = UserRole.LECTEUR
            )
            saveUserLocally(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inscription d'un nouvel utilisateur.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String,
        role: UserRole = UserRole.OUVRIER,
        licenseId: String = "",
        licenseName: String = ""
    ): Result<User> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID introuvable")
            val user = User(
                uid = uid,
                email = email,
                displayName = displayName,
                role = role,
                licenseId = licenseId,
                licenseName = licenseName,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            firebaseRepo.uploadUser(user)
            saveUserLocally(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        prefs.edit().clear().apply()
    }

    fun getCachedUser(): User? {
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val uid = firebaseAuth.currentUser?.uid ?: return null
        return User(
            uid = uid,
            email = email,
            displayName = prefs.getString(KEY_USER_NAME, "") ?: "",
            role = UserRole.valueOf(prefs.getString(KEY_USER_ROLE, UserRole.LECTEUR.name)!!),
            licenseId = prefs.getString(KEY_LICENSE_ID, "") ?: "",
            licenseName = prefs.getString(KEY_LICENSE_NAME, "") ?: "",
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
        val uid = firebaseAuth.currentUser?.uid ?: return
        // Mise à jour asynchrone sans attendre (fire & forget)
        // La synchro complète est gérée par SyncWorker
    }

    fun isBiometricEnabled() = prefs.getBoolean(KEY_BIOMETRIC, false)

    private fun saveUserLocally(user: User) {
        prefs.edit()
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.displayName)
            .putString(KEY_USER_ROLE, user.role.name)
            .putString(KEY_LICENSE_ID, user.licenseId)
            .putString(KEY_LICENSE_NAME, user.licenseName)
            .putBoolean(KEY_BIOMETRIC, user.biometricEnabled)
            .apply()
    }
}
