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
import com.taqnid.batch.domain.BatchRules

/**
 * Fragment de création d'un nouveau lot.
 */
class CreateBatchFragment : Fragment() {

    private var _binding: FragmentCreateBatchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        viewModel.busy.observe(viewLifecycleOwner) { busy -> binding.btnCreate.isEnabled = !busy }
        binding.btnCreate.setOnClickListener {
            if (!validate()) return@setOnClickListener

            val typeLabel = binding.actvType.text.toString()
            val type = BatchType.values().firstOrNull { it.labelFr == typeLabel } ?: BatchType.GRAINE
            viewModel.createBatch(
                variety = binding.etVariety.text.toString().trim(),
                type = type,
                initialQuantity = quantity(),
                unit = binding.etUnit.text.toString().trim(),
                locationName = binding.etLocation.text.toString().trim(),
                notes = binding.etNotes.text.toString().trim()
            )
        }
    }

    private fun quantity() = binding.etInitialQuantity.text.toString().replace(',', '.').trim().toDoubleOrNull()

    private fun validate(): Boolean {
        var valid = true
        if (binding.etVariety.text.isNullOrBlank()) {
            binding.tilVariety.error = "La variété est requise"
            valid = false
        } else binding.tilVariety.error = null

        val qtyCheck = BatchRules.checkInitialQuantity(quantity())
        if (qtyCheck is BatchRules.Check.Refused) {
            binding.tilInitialQuantity.error = qtyCheck.message
            valid = false
        } else binding.tilInitialQuantity.error = null

        val unitCheck = BatchRules.checkUnit(binding.etUnit.text.toString().trim())
        if (unitCheck is BatchRules.Check.Refused) {
            binding.tilUnit.error = unitCheck.message
            valid = false
        } else binding.tilUnit.error = null

        if (binding.etLocation.text.isNullOrBlank()) {
            binding.tilLocation.error = "L'emplacement est requis"
            valid = false
        } else binding.tilLocation.error = null

        return valid
    }

    private fun observeResult() {
        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
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
