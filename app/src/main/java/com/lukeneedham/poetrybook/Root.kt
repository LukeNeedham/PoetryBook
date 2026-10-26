package com.lukeneedham.poetrybook

import androidx.compose.runtime.Composable
import com.lukeneedham.poetrybook.navigation.AppNavigation
import com.lukeneedham.poetrybook.ui.theme.AppTheme
import com.lukeneedham.poetrybook.ui.theme.ProvideAppDebugSettings

/**
 * The one composable [MainActivity] calls: everything the UI sits in, then the navigation. Keep it wrapped in
 * [ProvideAppDebugSettings] even if no screen reads a setting yet, so a setting added to the library later reaches every
 * screen.
 */
@Composable
fun Root() {
    AppTheme {
        ProvideAppDebugSettings {
            AppNavigation()
        }
    }
}
