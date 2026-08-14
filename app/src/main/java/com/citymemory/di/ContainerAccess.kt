package com.citymemory.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.citymemory.CityMemoryApplication

/**
 * Bridges [CreationExtras] to the manual graph so each ViewModel can declare a
 * one-line `Factory` without any of them reaching for a static singleton.
 */
val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CityMemoryApplication)
        .container
