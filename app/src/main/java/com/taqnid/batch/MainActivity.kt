package com.taqnid.batch

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.taqnid.batch.data.local.AppDatabase
import com.taqnid.batch.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.taqnid.batch.databinding.ActivityMainBinding

/**
 * Activité principale — hôte du NavHostFragment et de la BottomNavigationView.
 * Accessible uniquement après connexion réussie.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Destinations où la BottomNav est visible (correspond aux items du menu).
    // homeFragment est la startDestination — BottomNav affichée mais sans item actif.
    // scannerFragment masque volontairement la BottomNav (plein écran caméra).
    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.batchListFragment,
        R.id.transferListFragment,
        R.id.reportsFragment,
        R.id.profileFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Navigation Component
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Configuration AppBar
        val appBarConfig = AppBarConfiguration(topLevelDestinations)
        setupActionBarWithNavController(navController, appBarConfig)

        // BottomNavigationView ↔ NavController
        binding.bottomNavigation.setupWithNavController(navController)

        observeSyncStatus()

        // Masquer/afficher la BottomNav selon la destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in topLevelDestinations) {
                binding.bottomNavigation.visibility = View.VISIBLE
            } else {
                binding.bottomNavigation.visibility = View.GONE
            }
        }
    }

    // ── Indicateurs de connexion et de synchronisation ──────────────────────────

    private val online = MutableStateFlow(false)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            online.value = true
            // Retour du réseau : envoyer sans attendre le prochain cycle
            SyncWorker.syncNow(applicationContext)
        }
        override fun onLost(network: Network) {
            online.value = false
        }
    }

    override fun onStart() {
        super.onStart()
        val cm = getSystemService(ConnectivityManager::class.java)
        online.value = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStop() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        super.onStop()
    }

    private fun observeSyncStatus() {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(online, db.observePendingCount(), db.observeProblemCount()) { isOnline, pending, problems ->
                    buildString {
                        append(getString(if (isOnline) R.string.status_online else R.string.status_offline))
                        append(" · ")
                        append(
                            if (pending == 0) getString(R.string.status_all_synced)
                            else getString(R.string.status_pending_count, pending)
                        )
                        if (problems > 0) append(" · ").append(getString(R.string.status_problem_count, problems))
                    }
                }.collect { supportActionBar?.subtitle = it }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
