package com.taqnid.batch.ui.reports

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.databinding.FragmentReportsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment de génération de rapports réglementaires (PDF et CSV).
 */
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDatePickers()
        setupButtons()
        observeData()

        // Charger les stats initiales
        viewModel.loadStats()
    }

    private fun setupDatePickers() {
        binding.btnFromDate.setOnClickListener {
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Date de début")
                .build()
                .also { picker ->
                    picker.addOnPositiveButtonClickListener { millis ->
                        viewModel.setFromDate(Date(millis))
                        viewModel.loadStats()
                    }
                    picker.show(parentFragmentManager, "FROM_DATE")
                }
        }

        binding.btnToDate.setOnClickListener {
            MaterialDatePicker.Builder.datePicker()
                .setTitleText("Date de fin")
                .build()
                .also { picker ->
                    picker.addOnPositiveButtonClickListener { millis ->
                        viewModel.setToDate(Date(millis))
                        viewModel.loadStats()
                    }
                    picker.show(parentFragmentManager, "TO_DATE")
                }
        }
    }

    private fun setupButtons() {
        binding.btnExportBatchPdf.setOnClickListener { viewModel.generateBatchReportPdf() }
        binding.btnExportTransferPdf.setOnClickListener { viewModel.generateTransferReportPdf() }
        binding.btnExportBatchCsv.setOnClickListener { viewModel.generateBatchReportCsv() }
        binding.btnExportActionCsv.setOnClickListener { viewModel.generateActionReportCsv() }
    }

    private fun observeData() {
        viewModel.fromDate.observe(viewLifecycleOwner) {
            binding.tvFromDate.text = "Du : ${dateFormat.format(it)}"
        }
        viewModel.toDate.observe(viewLifecycleOwner) {
            binding.tvToDate.text = "Au : ${dateFormat.format(it)}"
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvStatBatches.text = "Lots : ${stats.batchCount}"
                binding.tvStatActions.text = "Actions : ${stats.actionCount}"
                binding.tvStatTransfers.text = "Transferts : ${stats.transferCount}"
                binding.tvStatLoss.text = "Pertes : ${"%.2f".format(stats.totalLoss)} g (${"%.1f".format(stats.lossPercent)} %)"
            }
        }

        viewModel.isGenerating.observe(viewLifecycleOwner) { isGenerating ->
            binding.progressBar.visibility = if (isGenerating) View.VISIBLE else View.GONE
            binding.btnExportBatchPdf.isEnabled = !isGenerating
            binding.btnExportTransferPdf.isEnabled = !isGenerating
            binding.btnExportBatchCsv.isEnabled = !isGenerating
            binding.btnExportActionCsv.isEnabled = !isGenerating
        }

        viewModel.generatedFile.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.fold(
                onSuccess = { file ->
                    Snackbar.make(binding.root, "Fichier généré : ${file.name}", Snackbar.LENGTH_LONG)
                        .setAction("Ouvrir") { shareFile(file) }
                        .show()
                    viewModel.clearGeneratedFile()
                },
                onFailure = { err ->
                    Snackbar.make(binding.root, "Erreur : ${err.message}", Snackbar.LENGTH_LONG).show()
                    viewModel.clearGeneratedFile()
                }
            )
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            file
        )
        val mimeType = if (file.name.endsWith(".pdf")) "application/pdf" else "text/csv"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(intent, "Ouvrir le rapport"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
