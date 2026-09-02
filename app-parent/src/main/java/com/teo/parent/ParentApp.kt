package com.teo.parent

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import javax.inject.Inject

@HiltAndroidApp
class ParentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // osmdroid requires a distinct user agent to avoid tile-server bans, and a shared prefs store.
        OsmConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        OsmConfiguration.getInstance().userAgentValue = packageName
    }
}
