package com.taqnid.batch.ui.transfer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taqnid.batch.data.model.Batch
import com.taqnid.batch.databinding.ItemSelectedBatchBinding

/**
 * Adaptateur pour les lots sélectionnés dans la création de transfert.
 */
class SelectedBatchAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<Batch, SelectedBatchAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSelectedBatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemSelectedBatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(batch: Batch) {
            binding.tvTaqninId.text = batch.taqninId
            binding.tvVariety.text = batch.variety
            binding.tvQuantity.text = "${"%.1f".format(batch.currentQuantity)} ${batch.unit}"
            binding.btnRemove.setOnClickListener { onRemove(batch.id) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Batch>() {
            override fun areItemsTheSame(o: Batch, n: Batch) = o.id == n.id
            override fun areContentsTheSame(o: Batch, n: Batch) = o == n
        }
    }
}
