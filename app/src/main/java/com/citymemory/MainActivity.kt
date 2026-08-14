package com.citymemory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.citymemory.ui.CityMemoryApp
import com.citymemory.ui.LocalNavigationLauncher
import com.citymemory.ui.theme.CityMemoryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CityMemoryApplication).container

        setContent {
            CityMemoryTheme {
                CompositionLocalProvider(
                    LocalNavigationLauncher provides container.navigationLauncher,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        CityMemoryApp()
                    }
                }
            }
        }
    }
}
