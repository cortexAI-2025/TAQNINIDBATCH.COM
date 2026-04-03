package com.taqnid.batch.ui.transfer

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.databinding.FragmentCreateTransferBinding
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.ui.batch.BatchViewModel

/**
 * Fragment de création d'un manifeste de transfert.
 */
class CreateTransferFragment : Fragment() {

    private var _binding: FragmentCreateTransferBinding? = null
    private val binding get() = _binding!!

    private val transferViewModel: TransferViewModel by activityViewModels()
    private val batchViewModel: BatchViewModel by activityViewModels()
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userRepository = UserRepository(requireContext())

        setupSelectedBatchesList()
        setupSubmit()
        observeResults()
    }

    private fun setupSelectedBatchesList() {
        val selectedAdapter = SelectedBatchAdapter { batchId ->
            transferViewModel.removeBatchFromTransfer(batchId)
        }
        binding.recyclerViewSelectedBatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = selectedAdapter
        }

        transferViewModel.selectedBatches.observe(viewLifecycleOwner) { batches ->
            selectedAdapter.submitList(batches)
            binding.tvBatchCount.text = "${batches.size} lot(s) sélectionné(s)"
        }

        // Bouton pour ajouter un lot depuis la liste
        binding.btnAddBatch.setOnClickListener {
            // TODO: Ouvrir un dialogue de sélection de lot
            // Pour simplifier, on utilise le lot scanné en mémoire
            batchViewModel.scannedBatch.value?.let { batch ->
                transferViewModel.addBatchToTransfer(batch)
            } ?: Snackbar.make(
                binding.root,
                "Scannez d'abord un lot dans l'onglet Scanner",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupSubmit() {
        binding.btnCreateTransfer.setOnClickListener {
            if (!validate()) return@setOnClickListener

            val currentUser = userRepository.getCachedUser()
            transferViewModel.createTransfer(
                originLicenseId = currentUser?.licenseId ?: "",
                originLicenseName = currentUser?.licenseName ?: "",
                destinationLicenseId = binding.etDestinationLicense.text.toString().trim(),
                destinationLicenseName = binding.etDestinationName.text.toString().trim(),
                driverName = binding.etDriverName.text.toString().trim(),
                vehiclePlate = binding.etVehiclePlate.text.toString().trim(),
                createdBy = currentUser?.uid ?: "",
                createdByName = currentUser?.displayName ?: "Inconnu",
                notes = binding.etNotes.text.toString().trim()
            )
        }
    }

    private fun validate(): Boolean {
        var valid = true
        if (binding.etDestinationName.text.isNullOrBlank()) {
            binding.tilDestinationName.error = "Destinataire requis"
            valid = false
        } else binding.tilDestinationName.error = null

        if (binding.etDriverName.text.isNullOrBlank()) {
            binding.tilDriverName.error = "Conducteur requis"
            valid = false
        } else binding.tilDriverName.error = null

        if (transferViewModel.selectedBatches.value.isNullOrEmpty()) {
            Snackbar.make(binding.root, "Sélectionnez au moins un lot", Snackbar.LENGTH_SHORT).show()
            valid = false
        }
        return valid
    }

    private fun observeResults() {
        transferViewModel.operationResult.observe(viewLifecycleOwner) { result ->
            result.fold(
                onSuccess = { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                },
                onFailure = { err ->
                    Snackbar.make(binding.root, "Erreur : ${err.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
