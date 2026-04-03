package com.taqnid.batch.ui.transfer

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.taqnid.batch.R
import com.taqnid.batch.databinding.FragmentTransferListBinding

/**
 * Fragment — liste des manifestes de transfert.
 */
class TransferListFragment : Fragment() {

    private var _binding: FragmentTransferListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()
    private lateinit var adapter: TransferAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransferAdapter { transfer ->
            viewModel.selectTransfer(transfer.id)
            findNavController().navigate(
                R.id.action_transferListFragment_to_transferDetailFragment,
                bundleOf("transferId" to transfer.id)
            )
        }

        binding.recyclerViewTransfers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@TransferListFragment.adapter
        }

        binding.fabCreateTransfer.setOnClickListener {
            findNavController().navigate(R.id.action_transferListFragment_to_createTransferFragment)
        }

        viewModel.allTransfers.observe(viewLifecycleOwner) { transfers ->
            adapter.submitList(transfers)
            binding.tvEmptyState.visibility = if (transfers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
