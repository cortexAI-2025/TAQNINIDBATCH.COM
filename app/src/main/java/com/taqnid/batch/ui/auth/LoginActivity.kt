package com.taqnid.batch.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.MainActivity
import com.taqnid.batch.databinding.ActivityLoginBinding

/**
 * Écran de connexion — email/mot de passe + authentification biométrique.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Si déjà connecté, naviguer directement vers MainActivity
        if (viewModel.isAlreadyLoggedIn()) {
            if (viewModel.isBiometricEnabled()) {
                showBiometricPrompt()
            } else {
                navigateToMain()
            }
            return
        }

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Idle -> setLoading(false)
                is LoginState.Loading -> setLoading(true)
                is LoginState.Success -> {
                    setLoading(false)
                    navigateToMain()
                }
                is LoginState.Error -> {
                    setLoading(false)
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewModel.emailError.observe(this) { error ->
            binding.tilEmail.error = error
        }

        viewModel.passwordError.observe(this) { error ->
            binding.tilPassword.error = error
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            viewModel.login(email, password)
        }

        // Afficher/masquer l'option biométrique si disponible
        val canUseBiometric = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        binding.btnBiometric.visibility = if (canUseBiometric && viewModel.isBiometricEnabled())
            View.VISIBLE else View.GONE

        binding.btnBiometric.setOnClickListener {
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    navigateToMain()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Retomber sur le formulaire classique
                    binding.btnBiometric.visibility = View.GONE
                    Snackbar.make(
                        binding.root,
                        "Authentification biométrique échouée : $errString",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationFailed() {
                    Snackbar.make(
                        binding.root,
                        "Empreinte non reconnue",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Connexion biométrique")
            .setSubtitle("Taqnin ID Batch")
            .setDescription("Utilisez votre empreinte pour vous connecter")
            .setNegativeButtonText("Utiliser le mot de passe")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.etEmail.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
