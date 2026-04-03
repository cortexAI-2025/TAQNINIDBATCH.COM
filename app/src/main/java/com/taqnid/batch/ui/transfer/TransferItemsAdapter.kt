package com.taqnid.batch.ui.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taqnid.batch.data.model.TransferBatchItem
import com.taqnid.batch.databinding.ItemTransferBatchBinding

/**
 * Adaptateur pour la liste des lots dans un manifeste de transfert.
 */
class TransferItemsAdapter :
    ListAdapter<TransferBatchItem, TransferItemsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemTransferBatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemTransferBatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransferBatchItem) {
            binding.apply {
                tvTaqninId.text = item.taqninId
                tvVariety.text = item.variety
                tvStage.text = item.stage.labelFr
                tvQtyDeclared.text = "Déclaré : ${"%.2f".format(item.quantity)} ${item.unit}"

                if (item.quantityReceived != null) {
                    tvQtyReceived.text = "Reçu : ${"%.2f".format(item.quantityReceived)} ${item.unit}"
                    tvQtyReceived.visibility = android.view.View.VISIBLE

                    val discrepancy = item.discrepancy ?: 0.0
                    if (kotlin.math.abs(discrepancy) > 0.01) {
                        val sign = if (discrepancy >= 0) "+" else ""
                        tvDiscrepancy.text = "Écart : $sign${"%.2f".format(discrepancy)} ${item.unit}"
                        tvDiscrepancy.setTextColor(
                            binding.root.context.getColor(
                                if (discrepancy < 0) com.taqnid.batch.R.color.compliance_red
                                else com.taqnid.batch.R.color.compliance_green
                            )
                        )
                        tvDiscrepancy.visibility = android.view.View.VISIBLE
                    } else {
                        tvDiscrepancy.visibility = android.view.View.GONE
                    }
                } else {
                    tvQtyReceived.visibility = android.view.View.GONE
                    tvDiscrepancy.visibility = android.view.View.GONE
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransferBatchItem>() {
            override fun areItemsTheSame(o: TransferBatchItem, n: TransferBatchItem) = o.taqninId == n.taqninId
            override fun areContentsTheSame(o: TransferBatchItem, n: TransferBatchItem) = o == n
        }
    }
}
