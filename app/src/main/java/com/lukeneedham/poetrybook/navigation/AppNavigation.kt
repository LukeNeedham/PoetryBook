package com.lukeneedham.poetrybook.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.lukeneedham.common.debug.scale
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LocalAnimationSpeed
import com.lukeneedham.common.ui.LogsPage
import com.lukeneedham.poetrybook.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.poetrybook.ui.bin.BinBookPage
import com.lukeneedham.poetrybook.ui.bin.BinPoemPage
import com.lukeneedham.poetrybook.ui.bin.BinPage
import com.lukeneedham.poetrybook.ui.book.BookPage
import com.lukeneedham.poetrybook.ui.history.HistoryPage
import com.lukeneedham.poetrybook.ui.search.SearchPage
import com.lukeneedham.poetrybook.ui.settings.SettingsPage
import com.lukeneedham.poetrybook.ui.shelf.ShelfPage
import com.lukeneedham.poetrybook.ui.theme.paperColors

/** How long screens take to slide over one another, before the debug animation speed is applied. */
private const val ScreenTransitionMillis = 300

/** Going back: the screen being left slides away to the right, on top of the one revealed. */
private fun popTransition(transitionMillis: Int): ContentTransform =
    (
        slideInHorizontally(tween(transitionMillis)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(transitionMillis)) { it }
        ).apply { targetContentZIndex = -1f }

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
        entryProvider = entryProvider {
            entry<Route.Shelf> {
                ShelfPage(
                    onOpenBook = { backStack.add(Route.Book(it)) },
                    onOpenSettings = { backStack.add(Route.Settings) },
                    onOpenSearch = { backStack.add(Route.Search) },
                )
            }
            entry<Route.Book> { key ->
                BookPage(
                    bookId = key.bookId,
                    poemToShow = key.poemId,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenShelf = {
                        // Back to the shelf, even if the book was opened from a search.
                        while (backStack.size > 1 && backStack.lastOrNull() != Route.Shelf) backStack.removeLastOrNull()
                    },
                    onOpenVersion = { poemId, createdAt -> backStack.add(Route.History(poemId, createdAt)) },
                )
            }
            entry<Route.History> { key ->
                HistoryPage(
                    poemId = key.poemId,
                    createdAt = key.createdAt,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.Search> {
                SearchPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenPoem = { poem -> backStack.add(Route.Book(poem.bookId, poem.id)) },
                )
            }
            entry<Route.Settings> {
                SettingsPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBin = { backStack.add(Route.Bin) },
                    // Debug builds only: the debug pages are not registered in release.
                    onOpenDebug = if (BuildConfig.DEBUG) ({ backStack.add(Route.Debug) }) else null,
                )
            }
            entry<Route.Bin> {
                BinPage(
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBook = { backStack.add(Route.BinBook(it)) },
                    onOpenPoem = { backStack.add(Route.BinPoem(it)) },
                )
            }
            entry<Route.BinPoem> { key ->
                BinPoemPage(key.poemId, onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.BinBook> { key ->
                BinBookPage(key.bookId, onBack = { backStack.removeLastOrNull() })
            }
            // The debug pages are only registered in debug builds, so release has no route to them.
            if (BuildConfig.DEBUG) {
                entry<Route.Debug> {
                    DebugPage(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenLogs = { backStack.add(Route.Logs) },
                        onOpenCrashes = { backStack.add(Route.Crashes) },
                    )
                }
                entry<Route.Logs> { LogsPage(onBack = { backStack.removeLastOrNull() }) }
                entry<Route.Crashes> { CrashesPage(onBack = { backStack.removeLastOrNull() }) }
            }
        },
    )
}
