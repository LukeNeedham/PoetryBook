package com.lukeneedham.poetrybook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lukeneedham.common.ui.ProvideAnimationSpeed
import com.lukeneedham.poetrybook.navigation.AppNavigation
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme

/** The app's only activity. All screens are composables hosted by [AppNavigation]. */
class MainActivity : ComponentActivity() {
    private val viewModel: PoemBookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProvideAnimationSpeed {
                PoetryBookTheme {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.commitAll()
    }
}
