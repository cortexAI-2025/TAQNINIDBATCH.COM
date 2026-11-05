package com.taqnid.batch.ui.transfer

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.taqnid.batch.data.model.SyncState
import com.taqnid.batch.data.model.Transfer
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
        // Ouvert depuis la liste ou le scanner : l'argument fait foi
        arguments?.getString("transferId")?.let { viewModel.selectTransfer(it) }

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

                // Réception : seulement pour un manager de l'organisation destinataire
                val user = viewModel.currentUser
                val canReceive = transfer.status == TransferStatus.EN_TRANSIT &&
                    user != null && user.canTransfer() && user.licenseId == transfer.destinationLicenseId
                btnReceive.visibility = if (canReceive) View.VISIBLE else View.GONE
                if (transfer.syncState != SyncState.SYNCED) {
                    tvStatus.text = "${transfer.status.labelFr} — ${transfer.syncState.labelFr}" +
                        (transfer.syncMessage?.let { " : $it" } ?: "")
                }
            }
        }

        viewModel.busy.observe(viewLifecycleOwner) { busy -> binding.btnReceive.isEnabled = !busy }

        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
            val result = event.take() ?: return@observe
            result.fold(
                onSuccess = { msg -> Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show() },
                onFailure = { err -> Snackbar.make(binding.root, err.message ?: "Erreur", Snackbar.LENGTH_LONG).show() }
            )
        }
    }

    private fun setupButtons() {
        binding.btnReceive.setOnClickListener {
            val transfer = viewModel.selectedTransfer.value ?: return@setOnClickListener
            showReceptionDialog(transfer)
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

    /**
     * Réception : quantité constatée pour chaque lot (pré-remplie avec
     * l'expédié, jamais plus), ou refus du chargement.
     */
    private fun showReceptionDialog(transfer: Transfer) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val fields = transfer.batchSummaries.associate { item ->
            val field = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(item.quantity.toString())
                hint = "${item.taqninId} (${item.unit})"
            }
            container.addView(TextView(requireContext()).apply { text = "${item.taqninId} — expédié ${item.quantity} ${item.unit}" })
            container.addView(field)
            item.taqninId to field
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Quantités reçues")
            .setView(ScrollView(requireContext()).apply { addView(container) })
            .setNeutralButton("Refuser le chargement") { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Refuser le transfert ?")
                    .setMessage("Rien n'entrera dans votre stock. L'expéditeur verra le refus.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Refuser") { _, _ -> viewModel.rejectTransfer(transfer.id) }
                    .show()
            }
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Réceptionner") { _, _ ->
                val quantities = fields.mapValues { (_, f) -> f.text.toString().replace(',', '.').trim().toDoubleOrNull() ?: -1.0 }
                viewModel.receiveTransfer(transfer.id, quantities)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
