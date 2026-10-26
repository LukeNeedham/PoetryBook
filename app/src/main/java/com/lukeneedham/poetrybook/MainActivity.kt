package com.lukeneedham.poetrybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lukeneedham.poetrybook.navigation.AppNavigation
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.ProvideAppDebugSettings

/** The app's only activity. All screens are composables hosted by [AppNavigation]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PoetryBookTheme {
                ProvideAppDebugSettings {
                    AppNavigation()
                }
            }
        }
    }
}
