package com.taqnid.batch.ui.batch

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.taqnid.batch.R
import com.taqnid.batch.data.model.*
import com.taqnid.batch.databinding.FragmentBatchDetailBinding
import com.taqnid.batch.repository.UserRepository
import com.taqnid.batch.utils.QRCodeGenerator
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fragment de détail d'un lot — timeline des actions + QR code + actions rapides.
 */
class BatchDetailFragment : Fragment() {

    private var _binding: FragmentBatchDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by activityViewModels()
    private lateinit var actionAdapter: ActionTimelineAdapter
    private lateinit var userRepository: UserRepository

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
        userRepository = UserRepository(requireContext())

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
            if (batch == null) {
                findNavController().navigateUp()
                return@observe
            }
            displayBatch(batch)
        }

        viewModel.selectedBatchActions.observe(viewLifecycleOwner) { actions ->
            actionAdapter.submitList(actions.sortedByDescending { it.timestamp })
        }
    }

    private fun displayBatch(batch: Batch) {
        binding.apply {
            tvTaqninId.text = batch.taqninId
            tvVariety.text = batch.variety
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

            // Calcul du taux de perte
            val lossPercent = if (batch.initialQuantity > 0)
                ((batch.initialQuantity - batch.currentQuantity) / batch.initialQuantity * 100)
            else 0.0
            tvLossPercent.text = "Perte : ${"%.1f".format(lossPercent)} %"

            // Indicateur de conformité
            val (colorRes, label) = when (batch.complianceStatus) {
                ComplianceStatus.CONFORME -> Pair(R.color.compliance_green, "✓ Conforme")
                ComplianceStatus.ATTENTION -> Pair(R.color.compliance_orange, "⚠ Attention")
                ComplianceStatus.NON_CONFORME -> Pair(R.color.compliance_red, "✗ Non conforme")
                ComplianceStatus.EN_ATTENTE -> Pair(R.color.compliance_grey, "En attente")
            }
            val color = requireContext().getColor(colorRes)
            complianceBadge.text = label
            complianceBadge.setBackgroundColor(color)

            // Générer le QR code
            generateAndDisplayQR(batch.taqninId)
        }
    }

    private fun generateAndDisplayQR(taqninId: String) {
        val qrBitmap: Bitmap? = QRCodeGenerator.forTaqninId(taqninId, 400)
        qrBitmap?.let { binding.ivQrCode.setImageBitmap(it) }
    }

    private fun setupActions() {
        val currentUser = userRepository.getCachedUser()

        // Bouton changer de stade
        binding.btnChangeStage.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            showChangeStageDialog(batch, currentUser)
        }

        // Bouton enregistrer action (pesée, perte, etc.)
        binding.btnRecordAction.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            showRecordActionDialog(batch, currentUser)
        }

        // Bouton partager QR
        binding.btnShareQr.setOnClickListener {
            val batch = viewModel.selectedBatch.value ?: return@setOnClickListener
            val shareText = "Lot Taqnin ID : ${batch.taqninId}\nVariété : ${batch.variety}\nStade : ${batch.currentStage.labelFr}"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Partager le lot"))
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { result ->
            result.fold(
                onSuccess = { msg -> Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show() },
                onFailure = { err -> Snackbar.make(binding.root, "Erreur : ${err.message}", Snackbar.LENGTH_LONG).show() }
            )
        }
    }

    private fun showChangeStageDialog(batch: Batch, currentUser: User?) {
        val stages = BatchStage.values().filter { it != batch.currentStage }
        val stageLabels = stages.map { it.labelFr }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Changer le stade du lot")
            .setItems(stageLabels) { _, index ->
                val newStage = stages[index]
                viewModel.changeStage(
                    batch,
                    newStage,
                    currentUser?.uid ?: "",
                    currentUser?.displayName ?: "Inconnu"
                )
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showRecordActionDialog(batch: Batch, currentUser: User?) {
        val actionTypes = listOf(
            ActionType.PESEE,
            ActionType.PERTE,
            ActionType.AJOUT_EAU,
            ActionType.AJOUT_NUTRIMENT,
            ActionType.TRAITEMENT,
            ActionType.RETRAIT_TEST,
            ActionType.RETRAIT_VENTE,
            ActionType.NOTE
        )
        val labels = actionTypes.map { it.labelFr }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Type d'action")
            .setItems(labels) { _, index ->
                val actionType = actionTypes[index]
                showQuantityInputDialog(batch, actionType, currentUser)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showQuantityInputDialog(batch: Batch, actionType: ActionType, currentUser: User?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_record_action, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(actionType.labelFr)
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val etQty = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNewQuantity)
                val etDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDescription)
                val newQty = etQty.text?.toString()?.toDoubleOrNull() ?: batch.currentQuantity
                val desc = etDesc.text?.toString() ?: ""
                viewModel.recordAction(
                    batch, actionType, newQty,
                    currentUser?.uid ?: "",
                    currentUser?.displayName ?: "Inconnu",
                    desc
                )
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
