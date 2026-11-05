package com.taqnid.batch.ui.transfer

import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        // Choisir parmi les lots transférables de l'organisation (actifs, non vides)
        binding.btnAddBatch.setOnClickListener {
            val available = batchViewModel.transferableBatches.value.orEmpty()
                .filter { b -> transferViewModel.selectedBatches.value.orEmpty().none { it.id == b.id } }
            if (available.isEmpty()) {
                Snackbar.make(binding.root, "Aucun autre lot transférable", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ajouter un lot")
                .setItems(available.map { "${it.taqninId} — ${it.variety} (${it.currentQuantity} ${it.unit})" }.toTypedArray()) { _, i ->
                    transferViewModel.addBatchToTransfer(available[i])
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
        // Un lot scanné juste avant est proposé d'office
        batchViewModel.scannedBatch.value?.let { transferViewModel.addBatchToTransfer(it) }
        batchViewModel.transferableBatches.observe(viewLifecycleOwner) { }
    }

    private fun setupSubmit() {
        transferViewModel.busy.observe(viewLifecycleOwner) { busy -> binding.btnCreateTransfer.isEnabled = !busy }
        binding.btnCreateTransfer.setOnClickListener {
            if (!validate()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Expédier")
                .setMessage("Les lots sélectionnés sortiront de votre stock. Confirmer l'expédition ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Expédier") { _, _ ->
                    transferViewModel.createTransfer(
                        destinationLicenseId = binding.etDestinationLicense.text.toString().trim(),
                        destinationLicenseName = binding.etDestinationName.text.toString().trim(),
                        driverName = binding.etDriverName.text.toString().trim(),
                        vehiclePlate = binding.etVehiclePlate.text.toString().trim(),
                        notes = binding.etNotes.text.toString().trim()
                    )
                }
                .show()
        }
    }

    private fun validate(): Boolean {
        var valid = true
        if (binding.etDestinationName.text.isNullOrBlank()) {
            binding.tilDestinationName.error = "Destinataire requis"
            valid = false
        } else binding.tilDestinationName.error = null

        if (binding.etDestinationLicense.text.isNullOrBlank()) {
            binding.tilDestinationLicense.error = "Licence du destinataire requise"
            valid = false
        } else binding.tilDestinationLicense.error = null

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
        transferViewModel.operationResult.observe(viewLifecycleOwner) { event ->
            val result = event.take() ?: return@observe
            result.fold(
                onSuccess = { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                },
                onFailure = { err ->
                    Snackbar.make(binding.root, err.message ?: "Erreur", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
