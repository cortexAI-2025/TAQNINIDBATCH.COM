package com.taqnid.batch.ui.transfer

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.databinding.FragmentTransferDetailBinding
import com.taqnid.batch.data.model.TransferStatus
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.utils.PdfExporter
import com.taqnid.batch.utils.QRCodeGenerator
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fragment de détail d'un manifeste de transfert.
 * Permet de : visualiser le QR de transport, réceptionner le transfert, exporter le manifeste.
 */
class TransferDetailFragment : Fragment() {

    private var _binding: FragmentTransferDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()
    private lateinit var userRepository: UserRepository
    private lateinit var itemsAdapter: TransferItemsAdapter

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userRepository = UserRepository(requireContext())

        setupItemsList()
        observeData()
        setupButtons()
    }

    private fun setupItemsList() {
        itemsAdapter = TransferItemsAdapter()
        binding.recyclerViewItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = itemsAdapter
        }
    }

    private fun observeData() {
        viewModel.selectedTransfer.observe(viewLifecycleOwner) { transfer ->
            if (transfer == null) { findNavController().navigateUp(); return@observe }

            binding.apply {
                tvTransferNumber.text = transfer.transferNumber
                tvStatus.text = transfer.status.labelFr
                tvOrigin.text = "${transfer.originLicenseName} (${transfer.originLicenseId})"
                tvDestination.text = "${transfer.destinationLicenseName} (${transfer.destinationLicenseId})"
                tvDriver.text = transfer.driverName
                tvPlate.text = transfer.vehiclePlate
                tvCreatedAt.text = dateFormat.format(transfer.createdAt)
                tvDispatchedAt.text = transfer.dispatchedAt?.let { dateFormat.format(it) } ?: "-"
                tvReceivedAt.text = transfer.receivedAt?.let { dateFormat.format(it) } ?: "-"
                tvNotes.text = transfer.notes.ifBlank { "Aucune note" }

                itemsAdapter.submitList(transfer.batchSummaries)

                // QR code du manifeste
                val qrBitmap: Bitmap? = QRCodeGenerator.forTransfer(transfer.qrCodeContent, 350)
                qrBitmap?.let { ivQrCode.setImageBitmap(it) }

                // Bouton de réception : visible seulement si EN_TRANSIT
                btnReceive.visibility = if (transfer.status == TransferStatus.EN_TRANSIT)
                    View.VISIBLE else View.GONE
            }
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            result.fold(
                onSuccess = { msg -> Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show() },
                onFailure = { err -> Snackbar.make(binding.root, "Erreur : ${err.message}", Snackbar.LENGTH_LONG).show() }
            )
        }
    }

    private fun setupButtons() {
        binding.btnReceive.setOnClickListener {
            val transfer = viewModel.selectedTransfer.value ?: return@setOnClickListener
            val user = userRepository.getCachedUser()
            // Réception avec les quantités telles que déclarées (à personnaliser avec un dialogue)
            val quantities = transfer.batchSummaries.associate { it.taqninId to it.quantity }
            viewModel.receiveTransfer(transfer, user?.displayName ?: "Inconnu", quantities)
        }

        binding.btnExportManifest.setOnClickListener {
            val transfer = viewModel.selectedTransfer.value ?: return@setOnClickListener
            try {
                val file = PdfExporter.exportTransferManifest(requireContext(), transfer)
                val uri = FileProvider.getUriForFile(
                    requireContext(), "${requireContext().packageName}.provider", file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                startActivity(Intent.createChooser(intent, "Ouvrir le manifeste"))
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Erreur PDF : ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
