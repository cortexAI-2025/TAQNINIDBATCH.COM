package com.taqnid.batch.ui.batch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taqnid.batch.data.model.BatchAction
import com.taqnid.batch.databinding.ItemActionBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptateur pour la timeline verticale des actions d'un lot.
 */
class ActionTimelineAdapter :
    ListAdapter<BatchAction, ActionTimelineAdapter.ActionViewHolder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemActionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position), position == itemCount - 1)
    }

    inner class ActionViewHolder(
        private val binding: ItemActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(action: BatchAction, isLast: Boolean) {
            binding.apply {
                tvActionType.text = action.actionType.labelFr
                tvPerformedBy.text = "Par : ${action.performedByName}"
                tvTimestamp.text = dateFormat.format(action.timestamp)
                tvDescription.text = action.description

                // Variation de quantité
                val delta = action.quantityDelta
                if (delta != null) {
                    val sign = if (delta >= 0) "+" else ""
                    tvQuantityDelta.text = "$sign${"%.2f".format(delta)} ${action.unit}"
                    tvQuantityDelta.setTextColor(
                        binding.root.context.getColor(
                            if (delta >= 0) com.taqnid.batch.R.color.compliance_green
                            else com.taqnid.batch.R.color.compliance_red
                        )
                    )
                    tvQuantityDelta.visibility = android.view.View.VISIBLE
                } else {
                    tvQuantityDelta.visibility = android.view.View.GONE
                }

                // Changement de stade
                if (action.stageFrom != null && action.stageTo != null) {
                    tvStageChange.text = "${action.stageFrom.labelFr} → ${action.stageTo.labelFr}"
                    tvStageChange.visibility = android.view.View.VISIBLE
                } else {
                    tvStageChange.visibility = android.view.View.GONE
                }

                // Ligne de connexion timeline (masquée pour le dernier élément)
                timelineConnector.visibility =
                    if (isLast) android.view.View.INVISIBLE else android.view.View.VISIBLE
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BatchAction>() {
            override fun areItemsTheSame(oldItem: BatchAction, newItem: BatchAction) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: BatchAction, newItem: BatchAction) =
                oldItem == newItem
        }
    }
}
