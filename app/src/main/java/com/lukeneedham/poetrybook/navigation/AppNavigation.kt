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
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.poetrybook.ui.bin.BinBookPage
import com.lukeneedham.poetrybook.ui.bin.BinPoemPage
import com.lukeneedham.poetrybook.ui.bin.BinPage
import com.lukeneedham.poetrybook.ui.book.BookPage
import com.lukeneedham.poetrybook.ui.history.HistoryPage
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.search.SearchPage
import com.lukeneedham.poetrybook.ui.settings.SettingsPage
import com.lukeneedham.poetrybook.ui.shelf.ShelfPage
import com.lukeneedham.poetrybook.ui.theme.paperColors

/** How long screens take to slide over one another, before the debug animation speed is applied. */
private const val ScreenTransitionMillis = 300

/** How much longer than the (scaled) slide a closed book stays in memory. */
private const val BookClearExtraMillis = 200L

/** Going back: the screen being left slides away to the right, on top of the one revealed. */
private fun popTransition(transitionMillis: Int): ContentTransform =
    (
        slideInHorizontally(tween(transitionMillis)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(transitionMillis)) { it }
        ).apply { targetContentZIndex = -1f }

@Composable
fun AppNavigation(viewModel: PoemBookViewModel) {
    val backStack = rememberNavBackStack(ShelfNavKey)
    val transitionMillis = LocalAnimationSpeed.current.scale(ScreenTransitionMillis)
    // How long a closed book stays in memory, so its screen can still be drawn while it slides away.
    // It must outlast the slide, so it follows the scaled duration.
    val bookClearDelayMillis = transitionMillis + BookClearExtraMillis

    NavDisplay(
        // Paper behind the screens, so a transition never shows anything else through a gap.
        modifier = Modifier.background(paperColors().paper),
        backStack = backStack,
        // Screens slide over one another. They're all opaque paper, so unlike a crossfade nothing
        // is ever see-through (a crossfade over the white window background flashes in dark mode).
        transitionSpec = {
            slideInHorizontally(tween(transitionMillis)) { it } togetherWith
                slideOutHorizontally(tween(transitionMillis)) { -it / 3 }
        },
        popTransitionSpec = { popTransition(transitionMillis) },
        predictivePopTransitionSpec = { popTransition(transitionMillis) },
        onBack = {
            // Leaving a book by going back puts it on the shelf, like the shelf button does.
            if (backStack.lastOrNull() == BookNavKey) viewModel.closeBook(bookClearDelayMillis)
            backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<ShelfNavKey> {
                ShelfPage(
                    viewModel = viewModel,
                    onOpenBook = { backStack.add(BookNavKey) },
                    onOpenSettings = { backStack.add(SettingsNavKey) },
                    onOpenSearch = { backStack.add(SearchNavKey) },
                )
            }
            entry<BookNavKey> {
                // After the app was killed the back stack comes back but the open book doesn't.
                if (viewModel.currentBook == null) {
                    LaunchedEffect(Unit) {
                        if (backStack.lastOrNull() == BookNavKey) backStack.removeLastOrNull()
                    }
                    return@entry
                }
                BookPage(
                    viewModel = viewModel,
                    onOpenShelf = {
                        viewModel.closeBook(bookClearDelayMillis)
                        // Back to the shelf, even if the book was opened from a search.
                        while (backStack.size > 1 && backStack.lastOrNull() != ShelfNavKey) backStack.removeLastOrNull()
                    },
                    onOpenVersion = { poemId, createdAt -> backStack.add(HistoryNavKey(poemId, createdAt)) },
                )
            }
            entry<HistoryNavKey> { key ->
                HistoryPage(
                    viewModel = viewModel,
                    poemId = key.poemId,
                    createdAt = key.createdAt,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<SearchNavKey> {
                SearchPage(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenPoem = { poem ->
                        viewModel.openPoem(poem.bookId, poem.id) { ok -> if (ok) backStack.add(BookNavKey) }
                    },
                )
            }
            entry<SettingsNavKey> {
                SettingsPage(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBin = { backStack.add(BinNavKey) },
                    // Debug builds only: the debug pages are not registered in release.
                    onOpenDebug = if (BuildConfig.DEBUG) ({ backStack.add(DebugNavKey) }) else null,
                )
            }
            entry<BinNavKey> {
                BinPage(
                    viewModel = viewModel,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenBook = { backStack.add(BinBookNavKey(it)) },
                    onOpenPoem = { backStack.add(BinPoemNavKey(it)) },
                )
            }
            entry<BinPoemNavKey> { key ->
                BinPoemPage(viewModel, key.poemId, onBack = { backStack.removeLastOrNull() })
            }
            entry<BinBookNavKey> { key ->
                BinBookPage(viewModel, key.bookId, onBack = { backStack.removeLastOrNull() })
            }
            // The debug pages are only registered in debug builds, so release has no route to them.
            if (BuildConfig.DEBUG) {
                entry<DebugNavKey> {
                    DebugPage(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenLogs = { backStack.add(LogsNavKey) },
                        onOpenCrashes = { backStack.add(CrashesNavKey) },
                    )
                }
                entry<LogsNavKey> { LogsPage(onBack = { backStack.removeLastOrNull() }) }
                entry<CrashesNavKey> { CrashesPage(onBack = { backStack.removeLastOrNull() }) }
            }
        },
    )
}
