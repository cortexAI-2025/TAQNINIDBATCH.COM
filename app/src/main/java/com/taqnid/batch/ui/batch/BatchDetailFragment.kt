package com.taqnid.batch.ui.batch

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.R
import com.taqnid.batch.data.model.*
import com.taqnid.batch.databinding.FragmentBatchDetailBinding
import com.taqnid.batch.domain.BatchRules
import com.taqnid.batch.utils.QRCodeGenerator
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fragment de détail d'un lot — état, synchronisation, QR code, actions et
 * historique chronologique. Les boutons suivent le rôle : un lecteur
 * consulte, un ouvrier enregistre des pesées, un manager change le stade.
 */
class BatchDetailFragment : Fragment() {

    private var _binding: FragmentBatchDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by activityViewModels()
    private lateinit var actionAdapter: ActionTimelineAdapter

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatchDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Ouvert depuis la liste ou le scanner : l'argument fait foi
        arguments?.getString("batchId")?.let { viewModel.selectBatch(it) }

        setupTimeline()
        observeData()
        setupActions()
    }

    private fun setupTimeline() {
        actionAdapter = ActionTimelineAdapter()
        binding.recyclerViewTimeline.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = actionAdapter
        }
    }

    private fun observeData() {
        viewModel.selectedBatch.observe(viewLifecycleOwner) { batch ->
            if (batch == null) return@observe
            displayBatch(batch)
        }

        viewModel.selectedBatchActions.observe(viewLifecycleOwner) { actions ->
            // Historique chronologique : le plus récent en premier
            actionAdapter.submitList(actions.sortedByDescending { it.timestamp })
        }

        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.btnChangeStage.isEnabled = !busy
            binding.btnRecordAction.isEnabled = !busy
            binding.btnKeepLocal.isEnabled = !busy
            binding.btnKeepServer.isEnabled = !busy
        }
    }

    private fun displayBatch(batch: Batch) {
        binding.tvTaqninId.text = batch.taqninId
        binding.tvVariety.text = batch.variety
        binding.infoGrid.apply {
            tvType.text = batch.type.labelFr
            tvCurrentStage.text = batch.currentStage.labelFr
            tvCurrentQuantity.text = "${"%.2f".format(batch.currentQuantity)} ${batch.unit}"
            tvInitialQuantity.text = "${"%.2f".format(batch.initialQuantity)} ${batch.unit}"
            tvLocation.text = batch.locationName
            tvOwner.text = batch.ownerName
            tvLicense.text = batch.licenseNumber
            tvStartDate.text = dateFormat.format(batch.startDate)
            tvLastUpdated.text = dateFormat.format(batch.lastUpdated)
            tvNotes.text = batch.notes.ifBlank { "Aucune note" }

            batch.expirationDate?.let {
                tvExpirationDate.text = dateFormat.format(it)
                tvExpirationDate.visibility = View.VISIBLE
                labelExpiration.visibility = View.VISIBLE
            } ?: run {
                tvExpirationDate.visibility = View.GONE
                labelExpiration.visibility = View.GONE
            }

            val lossPercent = if (batch.initialQuantity > 0)
                ((batch.initialQuantity - batch.currentQuantity) / batch.initialQuantity * 100)
            else 0.0
            tvLossPercent.text = "Perte : ${"%.1f".format(lossPercent)} %"
        }

        val (colorRes, label) = when (batch.complianceStatus) {
            ComplianceStatus.CONFORME -> Pair(R.color.compliance_green, "✓ Conforme")
            ComplianceStatus.ATTENTION -> Pair(R.color.compliance_orange, "⚠ Attention")
            ComplianceStatus.NON_CONFORME -> Pair(R.color.compliance_red, "✗ Non conforme")
            ComplianceStatus.EN_ATTENTE -> Pair(R.color.compliance_grey, "En attente")
        }
        binding.complianceBadge.text = label
        binding.complianceBadge.setBackgroundColor(requireContext().getColor(colorRes))

        displaySync(batch)
        applyRole(batch)
        generateAndDisplayQR(batch.taqninId)
    }

    /** Bandeau : en attente d'envoi, conflit (avec choix), ou refus avec motif. */
    private fun displaySync(batch: Batch) {
        val (visible, title) = when (batch.syncState) {
            SyncState.SYNCED -> false to ""
            SyncState.PENDING -> true to getString(R.string.sync_pending)
            SyncState.CONFLICT -> true to getString(R.string.sync_conflict)
            SyncState.REJECTED -> true to getString(R.string.sync_rejected)
        }
        binding.cardSync.visibility = if (visible) View.VISIBLE else View.GONE
        binding.tvSyncState.text = title
        binding.tvSyncMessage.text = batch.syncMessage.orEmpty()
        binding.tvSyncMessage.visibility = if (batch.syncMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        val conflict = batch.syncState == SyncState.CONFLICT && viewModel.currentUser?.canCreate() == true
        binding.layoutConflictActions.visibility = if (conflict) View.VISIBLE else View.GONE
    }

    private fun applyRole(batch: Batch) {
        val user = viewModel.currentUser
        val active = !BatchRules.isTerminal(batch.currentStage)
        binding.btnChangeStage.visibility =
            if (user?.canCreate() == true && active) View.VISIBLE else View.GONE
        binding.btnRecordAction.visibility =
            if (user?.canScan() == true && active) View.VISIBLE else View.GONE
    }

    private fun generateAndDisplayQR(taqninId: String) {
        val qrBitmap: Bitmap? = QRCodeGenerator.forTaqninId(taqninId, 400)
        qrBitmap?.let { binding.ivQrCode.setImageBitmap(it) }
    }

    private fun setupActions() {
        binding.btnChangeStage.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            showChangeStageDialog(batch)
        }

        binding.btnRecordAction.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            showRecordActionDialog(batch)
        }

        binding.btnKeepServer.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            viewModel.keepServerVersion(batch)
        }

        binding.btnKeepLocal.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            viewModel.keepLocalVersion(batch)
        }

        binding.btnShareQr.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            val shareText = "Lot Taqnin ID : ${batch.taqninId}\nVariété : ${batch.variety}\nStade : ${batch.currentStage.labelFr}"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Partager le lot"))
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
            val result = event.take() ?: return@observe
            result.fold(
                onSuccess = { msg -> Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show() },
                onFailure = { err -> Snackbar.make(binding.root, err.message ?: getString(R.string.error_generic), Snackbar.LENGTH_LONG).show() }
            )
        }
    }

    private fun showChangeStageDialog(batch: Batch) {
        // Seules les transitions permises sont proposées
        val stages = BatchRules.allowedNextStages(batch.currentStage)
        if (stages.isEmpty()) {
            Snackbar.make(binding.root, "Aucun changement de stade possible", Snackbar.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Changer le stade du lot")
            .setItems(stages.map { it.labelFr }.toTypedArray()) { _, index ->
                val newStage = stages[index]
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirmer")
                    .setMessage("Passer de « ${batch.currentStage.labelFr} » à « ${newStage.labelFr} » ? Ce changement est définitif et tracé.")
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_confirm) { _, _ -> viewModel.changeStage(batch.id, newStage) }
                    .show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showRecordActionDialog(batch: Batch) {
        val actionTypes = listOf(
            ActionType.PESEE,
            ActionType.PERTE,
            ActionType.AJOUT_EAU,
            ActionType.AJOUT_NUTRIMENT,
            ActionType.TRAITEMENT,
            ActionType.RETRAIT_TEST,
            ActionType.RETRAIT_VENTE,
            ActionType.RETRAIT_DESTRUCTION,
            ActionType.NOTE
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Type d'action")
            .setItems(actionTypes.map { it.labelFr }.toTypedArray()) { _, index ->
                showQuantityInputDialog(batch, actionTypes[index])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showQuantityInputDialog(batch: Batch, actionType: ActionType) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_action, null)
        val etQty = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNewQuantity)
        val etDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
        etQty.hint = "Nouvelle quantité (${batch.unit}) — actuelle : ${batch.currentQuantity}"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(actionType.labelFr)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                // Champ vide ou illisible : refusé par les règles, jamais remplacé par une valeur implicite
                val newQty = etQty.text?.toString()?.replace(',', '.')?.trim()?.toDoubleOrNull()
                viewModel.recordAction(batch.id, actionType, newQty, etDesc.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
