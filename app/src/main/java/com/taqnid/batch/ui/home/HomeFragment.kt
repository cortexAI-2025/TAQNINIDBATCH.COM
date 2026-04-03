package com.taqnid.batch.ui.home

import android.os.Bundle
import android.view.*
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.taqnid.batch.R
import com.taqnid.batch.databinding.FragmentHomeBinding
import com.taqnid.batch.ui.batch.BatchAdapter
import com.taqnid.batch.ui.batch.BatchViewModel
import com.taqnid.batch.worker.SyncWorker

/**
 * Fragment d'accueil — tableau de bord.
 * Affiche : bouton scanner rapide, lots récents, alertes actives.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val batchViewModel: BatchViewModel by activityViewModels()
    private lateinit var recentAdapter: BatchAdapter
    private lateinit var alertAdapter: BatchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupClickListeners()
        observeData()
    }

    private fun setupRecyclerViews() {
        recentAdapter = BatchAdapter { batch ->
            batchViewModel.selectBatch(batch.id)
            findNavController().navigate(
                R.id.action_homeFragment_to_batchDetailFragment,
                bundleOf("batchId" to batch.id)
            )
        }
        binding.recyclerViewRecent.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }

        alertAdapter = BatchAdapter { batch ->
            batchViewModel.selectBatch(batch.id)
            findNavController().navigate(
                R.id.action_homeFragment_to_batchDetailFragment,
                bundleOf("batchId" to batch.id)
            )
        }
        binding.recyclerViewAlerts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alertAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        // Bouton scanner rapide → onglet scanner
        binding.btnQuickScan.setOnClickListener {
            findNavController().navigate(R.id.scannerFragment)
        }

        // Bouton créer un lot
        binding.btnCreateBatch.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_createBatchFragment)
        }

        // Bouton synchroniser manuellement
        binding.btnSync.setOnClickListener {
            SyncWorker.syncNow(requireContext())
            binding.btnSync.isEnabled = false
            binding.btnSync.postDelayed({ binding.btnSync.isEnabled = true }, 5000)
        }
    }

    private fun observeData() {
        batchViewModel.activeBatchCount.observe(viewLifecycleOwner) { count ->
            binding.tvActiveBatchCount.text = "$count lot(s) actif(s)"
        }

        batchViewModel.recentBatches.observe(viewLifecycleOwner) { batches ->
            recentAdapter.submitList(batches)
            binding.tvNoRecent.visibility = if (batches.isEmpty()) View.VISIBLE else View.GONE
        }

        // Alertes : lots expirant dans 7 jours + stock bas
        batchViewModel.expiringBatches.observe(viewLifecycleOwner) { expiring ->
            batchViewModel.lowStockBatches.observe(viewLifecycleOwner) { lowStock ->
                val alerts = (expiring + lowStock).distinctBy { it.id }
                alertAdapter.submitList(alerts)
                binding.tvAlertCount.text = "${alerts.size} alerte(s)"
                binding.cardAlerts.visibility = if (alerts.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
