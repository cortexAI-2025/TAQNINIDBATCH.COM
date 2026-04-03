package com.taqnid.batch.ui.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taqnid.batch.R
import com.taqnid.batch.data.model.Transfer
import com.taqnid.batch.data.model.TransferStatus
import com.taqnid.batch.databinding.ItemTransferBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptateur RecyclerView pour la liste des transferts.
 */
class TransferAdapter(
    private val onItemClick: (Transfer) -> Unit
) : ListAdapter<Transfer, TransferAdapter.TransferViewHolder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
        val binding = ItemTransferBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TransferViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TransferViewHolder(
        private val binding: ItemTransferBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(transfer: Transfer) {
            binding.apply {
                tvTransferNumber.text = transfer.transferNumber
                tvOrigin.text = transfer.originLicenseName
                tvDestination.text = transfer.destinationLicenseName
                tvBatchCount.text = "${transfer.batchIds.size} lot(s)"
                tvCreatedAt.text = dateFormat.format(transfer.createdAt)
                tvDriver.text = "Conducteur : ${transfer.driverName}"

                val (colorRes, label) = when (transfer.status) {
                    TransferStatus.EN_TRANSIT -> Pair(R.color.status_transit, "En transit")
                    TransferStatus.RECEPTIONNE -> Pair(R.color.compliance_green, "Réceptionné")
                    TransferStatus.REJETE -> Pair(R.color.compliance_red, "Rejeté")
                    TransferStatus.ANNULE -> Pair(R.color.compliance_grey, "Annulé")
                    TransferStatus.BROUILLON -> Pair(R.color.compliance_orange, "Brouillon")
                }
                tvStatus.text = label
                tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, colorRes))

                root.setOnClickListener { onItemClick(transfer) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Transfer>() {
            override fun areItemsTheSame(oldItem: Transfer, newItem: Transfer) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Transfer, newItem: Transfer) =
                oldItem == newItem
        }
    }
}
