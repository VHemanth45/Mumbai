package com.citymemory

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.citymemory.di.AppContainer

class CityMemoryApplication : Application(), Configuration.Provider {

    /**
     * Built lazily so process creation stays cheap — the database is not opened
     * until the first screen actually asks for data.
     */
    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * Supplied on demand, because WorkManager's automatic initialiser is
     * removed in the manifest.
     *
     * This getter runs the first time anything actually touches WorkManager —
     * scheduling the dwell sampler, or the system starting a job — rather than
     * on every cold start the way the default ContentProvider would.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            // Errors only. The sampler runs all day and has nothing to say on
            // a normal run; anything chattier would be writing to logcat every
            // fifteen minutes for the life of the install.
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
}
