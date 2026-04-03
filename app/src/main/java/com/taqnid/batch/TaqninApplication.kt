package com.taqnid.batch

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.taqnid.batch.worker.SyncWorker

/**
 * Classe Application — initialise les composants globaux.
 */
class TaqninApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Planifier la synchronisation périodique avec Firebase
        SyncWorker.schedulePeriodic(this)
    }
}
