package com.taqnid.batch.ui.batch

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.taqnid.batch.R
import com.taqnid.batch.databinding.FragmentBatchListBinding

/**
 * Fragment — liste de tous les lots avec recherche et filtres.
 */
class BatchListFragment : Fragment() {

    private var _binding: FragmentBatchListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BatchViewModel by activityViewModels()
    private lateinit var adapter: BatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatchListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupFab()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = BatchAdapter { batch ->
            // Naviguer vers le détail du lot
            viewModel.selectBatch(batch.id)
            findNavController().navigate(
                R.id.action_batchListFragment_to_batchDetailFragment,
                bundleOf("batchId" to batch.id)
            )
        }
        binding.recyclerViewBatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BatchListFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true.also {
                viewModel.setSearchQuery(query ?: "")
            }
            override fun onQueryTextChange(newText: String?) = true.also {
                viewModel.setSearchQuery(newText ?: "")
            }
        })
    }

    private fun setupFab() {
        binding.fabCreateBatch.setOnClickListener {
            findNavController().navigate(R.id.action_batchListFragment_to_createBatchFragment)
        }
    }

    private fun observeData() {
        viewModel.searchResults.observe(viewLifecycleOwner) { batches ->
            adapter.submitList(batches)
            binding.tvEmptyState.visibility =
                if (batches.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
