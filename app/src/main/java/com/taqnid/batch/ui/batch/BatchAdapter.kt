package com.taqnid.batch.ui.batch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taqnid.batch.R
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.data.model.ComplianceStatus
import com.taqnid.batch.databinding.ItemBatchBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptateur RecyclerView pour la liste des lots.
 */
class BatchAdapter(
    private val onItemClick: (Batch) -> Unit
) : ListAdapter<Batch, BatchAdapter.BatchViewHolder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatchViewHolder {
        val binding = ItemBatchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BatchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BatchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BatchViewHolder(
        private val binding: ItemBatchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(batch: Batch) {
            binding.apply {
                tvTaqninId.text = batch.taqninId
                tvVariety.text = batch.variety
                tvStage.text = batch.currentStage.labelFr
                tvQuantity.text = "${"%.1f".format(batch.currentQuantity)} ${batch.unit}"
                tvLocation.text = batch.locationName
                tvLastUpdated.text = "Mis à jour : ${dateFormat.format(batch.lastUpdated)}"

                // Indicateur de conformité (vert / orange / rouge)
                val (colorRes, iconRes) = when (batch.complianceStatus) {
                    ComplianceStatus.CONFORME -> Pair(R.color.compliance_green, R.drawable.ic_check_circle)
                    ComplianceStatus.ATTENTION -> Pair(R.color.compliance_orange, R.drawable.ic_warning)
                    ComplianceStatus.NON_CONFORME -> Pair(R.color.compliance_red, R.drawable.ic_error)
                    ComplianceStatus.EN_ATTENTE -> Pair(R.color.compliance_grey, R.drawable.ic_pending)
                }
                val color = ContextCompat.getColor(binding.root.context, colorRes)
                ivComplianceIndicator.setColorFilter(color)
                ivComplianceIndicator.setImageResource(iconRes)
                tvComplianceStatus.text = batch.complianceStatus.labelFr
                tvComplianceStatus.setTextColor(color)

                // Indicateur de synchronisation
                ivSyncStatus.setImageResource(
                    if (batch.isSynced) R.drawable.ic_cloud_done else R.drawable.ic_cloud_off
                )

                root.setOnClickListener { onItemClick(batch) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Batch>() {
            override fun areItemsTheSame(oldItem: Batch, newItem: Batch) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Batch, newItem: Batch) =
                oldItem == newItem
        }
    }
}
