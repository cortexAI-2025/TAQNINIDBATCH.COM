package com.taqnid.batch.ui.profile

import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.taqnid.batch.R
import com.taqnid.batch.data.local.AppDatabase
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.databinding.FragmentProfileBinding
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.ui.auth.LoginActivity

/**
 * Fragment de profil utilisateur — affiche les infos et paramètres du compte.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userRepository = UserRepository(requireContext())

        displayUserInfo()
        setupBiometricToggle()
        setupLogout()
    }

    private fun displayUserInfo() {
        val user = userRepository.getCachedUser()
        binding.apply {
            tvDisplayName.text = user?.displayName ?: "Utilisateur inconnu"
            tvEmail.text = user?.email ?: "-"
            tvRole.text = user?.role?.labelFr ?: "-"
            tvLicense.text = user?.licenseName?.ifBlank { "Non renseigné" } ?: "Non renseigné"
        }
    }

    private fun setupBiometricToggle() {
        val canUseBiometric = BiometricManager.from(requireContext())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

        binding.switchBiometric.isEnabled = canUseBiometric
        binding.switchBiometric.isChecked = userRepository.isBiometricEnabled()

        if (!canUseBiometric) {
            binding.tvBiometricStatus.text = "Biométrie non disponible sur cet appareil"
        }

        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            userRepository.setBiometricEnabled(isChecked)
            Snackbar.make(
                binding.root,
                if (isChecked) "Connexion biométrique activée" else "Connexion biométrique désactivée",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // Se déconnecter efface l'appareil : prévenir s'il reste des saisies non envoyées
                val pending = AppDatabase.getInstance(requireContext()).pendingCount()
                if (pending == 0) {
                    logout()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.logout_pending_title)
                        .setMessage(getString(R.string.logout_pending_message, pending))
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.logout_anyway) { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch { logout() }
                        }
                        .show()
                }
            }
        }
    }

    private suspend fun logout() {
        userRepository.logout()
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
