package com.taqnid.batch.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.data.model.User
import com.taqnid.batch.data.remote.FirebaseRepository
import com.taqnid.batch.repository.UserRepository
import kotlinx.coroutines.launch

/**
 * États possibles de l'écran de connexion.
 */
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: User) : LoginState()
    data class Error(val message: String) : LoginState()
}

/**
 * ViewModel pour l'écran de connexion.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application)

    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    fun isAlreadyLoggedIn() = userRepository.isLoggedIn()
    fun getCachedUser() = userRepository.getCachedUser()
    fun isBiometricEnabled() = userRepository.isBiometricEnabled()

    /**
     * Tente une connexion par email / mot de passe.
     */
    fun login(email: String, password: String) {
        // Validation locale
        var valid = true
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _emailError.value = "Adresse e-mail invalide"
            valid = false
        } else {
            _emailError.value = null
        }
        if (password.length < 6) {
            _passwordError.value = "Le mot de passe doit contenir au moins 6 caractères"
            valid = false
        } else {
            _passwordError.value = null
        }
        if (!valid) return

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = userRepository.login(email, password)
            _loginState.value = result.fold(
                onSuccess = { LoginState.Success(it) },
                onFailure = { LoginState.Error(mapFirebaseError(it.message)) }
            )
        }
    }

    fun logout() = userRepository.logout()

    private fun mapFirebaseError(message: String?): String {
        return when {
            message?.contains("password") == true -> "Mot de passe incorrect"
            message?.contains("no user") == true -> "Aucun compte trouvé pour cet e-mail"
            message?.contains("network") == true -> "Erreur réseau — vérifiez votre connexion"
            message?.contains("blocked") == true -> "Trop de tentatives — réessayez plus tard"
            else -> "Erreur de connexion : ${message ?: "inconnue"}"
        }
    }
}
