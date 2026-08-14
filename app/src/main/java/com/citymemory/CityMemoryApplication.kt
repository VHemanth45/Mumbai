package com.citymemory

import android.app.Application
import com.citymemory.di.AppContainer

class CityMemoryApplication : Application() {

    /**
     * Built lazily so process creation stays cheap — the database is not opened
     * until the first screen actually asks for data.
     */
    val container: AppContainer by lazy { AppContainer(this) }
}
