package com.taqnid.batch.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.User
import com.taqnid.batch.data.model.UserRole
import com.taqnid.batch.data.remote.FirebaseRepository
import com.taqnid.batch.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Connexion refusée pour une raison que l'utilisateur doit lire. */
class AccountNotReadyException(message: String) : Exception(message)

/**
 * Repository utilisateur — authentification Firebase et profil.
 *
 * Le rôle, l'organisation et l'activation du compte viennent toujours du
 * profil serveur /users/{uid}, que l'application ne peut pas modifier sur ces
 * champs (règles Firestore). L'inscription crée un profil en attente :
 * LECTEUR, sans organisation, inactif, jusqu'à son rattachement par un
 * administrateur via l'outil SDK Admin (scripts/assign-user.js).
 *
 * Les données locales d'un compte ne sont jamais montrées à un autre : elles
 * sont effacées à la déconnexion et quand un autre compte se connecte sur
 * l'appareil.
 */
class UserRepository(
    private val context: Context,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firebaseRepo: FirebaseRepository = FirebaseRepository()
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("taqnin_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_UID = "user_uid"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_LICENSE_NAME = "license_name"
        private const val KEY_ACTIVE = "is_active"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        // Dernier compte connecté sur l'appareil, conservé après déconnexion
        private const val KEY_LAST_UID = "last_uid"
    }

    fun getCurrentFirebaseUser() = firebaseAuth.currentUser

    fun isLoggedIn() = firebaseAuth.currentUser != null && getCachedUser()?.isActive == true

    /**
     * Connexion par email / mot de passe. Le profil serveur est obligatoire
     * et doit être actif et rattaché à une organisation.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: throw Exception("UID introuvable")
            val uid = firebaseUser.uid
            var user = firebaseRepo.getUser(uid)
            if (user == null) {
                // Premier accès d'un compte créé dans la console : profil en attente
                user = User(
                    uid = uid,
                    email = firebaseUser.email ?: email,
                    displayName = firebaseUser.displayName ?: email.substringBefore("@"),
                    role = UserRole.LECTEUR,
                    licenseId = "",
                    licenseName = "",
                    isActive = false,
                )
                firebaseRepo.createPendingProfile(user)
            }
            if (!user.isActive || user.licenseId.isBlank()) {
                firebaseAuth.signOut()
                throw AccountNotReadyException(
                    "Compte en attente : un administrateur doit le rattacher à votre organisation et l'activer."
                )
            }
            switchLocalAccount(uid)
            saveUserLocally(user)
            firebaseRepo.touchLastLogin(uid)
            SyncWorker.schedulePeriodic(context)
            SyncWorker.syncNow(context)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inscription : le compte est créé en attente, sans rôle ni organisation.
     * Aucun paramètre ne permet de choisir son rôle.
     */
    suspend fun register(email: String, password: String, displayName: String): Result<User> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID introuvable")
            val user = User(
                uid = uid,
                email = email,
                displayName = displayName.take(100),
                role = UserRole.LECTEUR,
                licenseId = "",
                licenseName = "",
                isActive = false,
                createdAt = System.currentTimeMillis()
            )
            firebaseRepo.createPendingProfile(user)
            firebaseAuth.signOut()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Relit le profil serveur (rôle changé, compte désactivé) pendant la synchronisation. */
    suspend fun refreshProfile(): User? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val user = firebaseRepo.getUser(uid) ?: return null
        saveUserLocally(user)
        return user
    }

    /**
     * Déconnexion. Les données locales sont effacées : un autre compte
     * pourrait utiliser l'appareil. Le contrôle des saisies non envoyées se
     * fait avant l'appel (voir ProfileFragment).
     */
    suspend fun logout() {
        SyncWorker.cancelAll(context)
        firebaseAuth.signOut()
        wipeLocalData()
        prefs.edit()
            .remove(KEY_UID).remove(KEY_USER_ROLE).remove(KEY_USER_NAME).remove(KEY_USER_EMAIL)
            .remove(KEY_LICENSE_ID).remove(KEY_LICENSE_NAME).remove(KEY_ACTIVE).remove(KEY_BIOMETRIC)
            .apply()
    }

    fun getCachedUser(): User? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        if (prefs.getString(KEY_UID, null) != uid) return null
        return User(
            uid = uid,
            email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
            displayName = prefs.getString(KEY_USER_NAME, "") ?: "",
            role = runCatching { UserRole.valueOf(prefs.getString(KEY_USER_ROLE, null)!!) }.getOrDefault(UserRole.LECTEUR),
            licenseId = prefs.getString(KEY_LICENSE_ID, "") ?: "",
            licenseName = prefs.getString(KEY_LICENSE_NAME, "") ?: "",
            isActive = prefs.getBoolean(KEY_ACTIVE, false),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    fun isBiometricEnabled() = prefs.getBoolean(KEY_BIOMETRIC, false)

    /** Un autre compte que le précédent : rien de ce qui est sur l'appareil ne lui appartient. */
    private suspend fun switchLocalAccount(uid: String) {
        val previous = prefs.getString(KEY_LAST_UID, null)
        if (previous != null && previous != uid) wipeLocalData()
        prefs.edit().putString(KEY_LAST_UID, uid).apply()
    }

    private suspend fun wipeLocalData() = withContext(Dispatchers.IO) {
        AppDatabase.getInstance(context).clearAllTables()
        // La prochaine synchronisation récupère tout l'historique du nouveau compte
        context.getSharedPreferences("taqnin_sync", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun saveUserLocally(user: User) {
        prefs.edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.displayName)
            .putString(KEY_USER_ROLE, user.role.name)
            .putString(KEY_LICENSE_ID, user.licenseId)
            .putString(KEY_LICENSE_NAME, user.licenseName)
            .putBoolean(KEY_ACTIVE, user.isActive)
            .putBoolean(KEY_BIOMETRIC, user.biometricEnabled)
            .apply()
    }
}
