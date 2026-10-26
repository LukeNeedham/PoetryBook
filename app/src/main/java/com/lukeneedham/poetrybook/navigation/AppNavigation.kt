package com.lukeneedham.poetrybook.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.lukeneedham.common.debug.scale
import com.lukeneedham.common.ui.LocalAnimationSpeed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.poetrybook.ui.theme.paperColors

/** How long screens take to slide over one another, before the debug animation speed is applied. */
private const val ScreenTransitionMillis = 300

/** Going back: the screen being left slides away to the right, on top of the one revealed. */
private fun popTransition(transitionMillis: Int): ContentTransform =
    (
        slideInHorizontally(tween(transitionMillis)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(transitionMillis)) { it }
        ).apply { targetContentZIndex = -1f }

/** Owns the back stack and the [NavDisplay]. Called only by `Root`. */
@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(Route.Shelf)
    val transitionMillis = LocalAnimationSpeed.current.scale(ScreenTransitionMillis)

    NavDisplay(
        // Paper behind the screens, so a transition never shows anything else through a gap.
        modifier = Modifier.background(paperColors().paper),
        backStack = backStack,
        // Each page has a view model of its own, kept for as long as the page is on the back stack
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Screens slide over one another. They're all opaque paper, so unlike a crossfade nothing
        // is ever see-through (a crossfade over the white window background flashes in dark mode).
        transitionSpec = {
            slideInHorizontally(tween(transitionMillis)) { it } togetherWith
                slideOutHorizontally(tween(transitionMillis)) { -it / 3 }
        },
        popTransitionSpec = { popTransition(transitionMillis) },
        predictivePopTransitionSpec = { popTransition(transitionMillis) },
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key -> routeEntryProvider(backStack, key) },
    )
}

private fun routeEntryProvider(backStack: NavBackStack<NavKey>, key: NavKey): NavEntry<NavKey> {
    val route = key as Route
    return NavEntry(route) {
        Router(backStack, route)
    }
}
