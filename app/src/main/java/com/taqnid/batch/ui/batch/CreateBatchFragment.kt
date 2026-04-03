package com.taqnid.batch.ui.batch

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.data.model.BatchType
import com.taqnid.batch.databinding.FragmentCreateBatchBinding
import com.taqnid.batch.repository.UserRepository

/**
 * Fragment de création d'un nouveau lot.
 */
class CreateBatchFragment : Fragment() {

    private var _binding: FragmentCreateBatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by activityViewModels()
    private lateinit var userRepository: UserRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userRepository = UserRepository(requireContext())

        setupTypeSpinner()
        setupSubmit()
        observeResult()
    }

    private fun setupTypeSpinner() {
        val types = BatchType.values().map { it.labelFr }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        binding.actvType.setAdapter(adapter)
        binding.actvType.setText(types[0], false)
    }

    private fun setupSubmit() {
        binding.btnCreate.setOnClickListener {
            if (!validate()) return@setOnClickListener

            val currentUser = userRepository.getCachedUser()
            val variety = binding.etVariety.text.toString().trim()
            val typeLabel = binding.actvType.text.toString()
            val type = BatchType.values().firstOrNull { it.labelFr == typeLabel } ?: BatchType.GRAINE
            val qty = binding.etInitialQuantity.text.toString().toDoubleOrNull() ?: 0.0
            val unit = binding.etUnit.text.toString().trim().ifBlank { "g" }
            val locationName = binding.etLocation.text.toString().trim()
            val notes = binding.etNotes.text.toString().trim()

            viewModel.createBatch(
                variety = variety,
                type = type,
                initialQuantity = qty,
                unit = unit,
                locationId = "",           // À relier à un LocationDao en production
                locationName = locationName,
                ownerId = currentUser?.uid ?: "",
                ownerName = currentUser?.displayName ?: "Inconnu",
                licenseNumber = currentUser?.licenseId ?: "",
                notes = notes
            )
        }
    }

    private fun validate(): Boolean {
        var valid = true
        if (binding.etVariety.text.isNullOrBlank()) {
            binding.tilVariety.error = "La variété est requise"
            valid = false
        } else binding.tilVariety.error = null

        val qty = binding.etInitialQuantity.text.toString().toDoubleOrNull()
        if (qty == null || qty <= 0) {
            binding.tilInitialQuantity.error = "Quantité invalide"
            valid = false
        } else binding.tilInitialQuantity.error = null

        if (binding.etLocation.text.isNullOrBlank()) {
            binding.tilLocation.error = "L'emplacement est requis"
            valid = false
        } else binding.tilLocation.error = null

        return valid
    }

    private fun observeResult() {
        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
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
