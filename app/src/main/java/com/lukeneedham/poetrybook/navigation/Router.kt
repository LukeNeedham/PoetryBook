package com.lukeneedham.poetrybook.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.lukeneedham.common.ui.CrashesPage
import com.lukeneedham.common.ui.DebugPage
import com.lukeneedham.common.ui.LogsPage
import com.lukeneedham.poetrybook.BuildConfig
import com.lukeneedham.poetrybook.ui.bin.BinBookPage
import com.lukeneedham.poetrybook.ui.bin.BinPage
import com.lukeneedham.poetrybook.ui.bin.BinPoemPage
import com.lukeneedham.poetrybook.ui.book.BookPage
import com.lukeneedham.poetrybook.ui.history.HistoryPage
import com.lukeneedham.poetrybook.ui.search.SearchPage
import com.lukeneedham.poetrybook.ui.settings.SettingsPage
import com.lukeneedham.poetrybook.ui.shelf.ShelfPage

/** Calls the page of [route], with the navigation as lambdas. The only place that calls a page. */
@Composable
fun Router(backStack: NavBackStack<NavKey>, route: Route) {
    // The common actions are defined once here, never repeated in each branch
    fun navigateTo(target: Route) { backStack.add(target) }
    fun onBack() { backStack.removeLastOrNull() }

    // Back to the shelf, even if the book was opened from a search.
    fun navigateBackToShelf() {
        while (backStack.size > 1 && backStack.lastOrNull() != Route.Shelf) backStack.removeLastOrNull()
    }

    when (route) { // exhaustive: no `else`
        Route.Shelf -> ShelfPage(
            onOpenBook = { navigateTo(Route.Book(it)) },
            onOpenSettings = { navigateTo(Route.Settings) },
            onOpenSearch = { navigateTo(Route.Search) },
        )

        is Route.Book -> BookPage(
            bookId = route.bookId,
            poemToShow = route.poemId,
            onBack = ::onBack,
            onOpenShelf = ::navigateBackToShelf,
            onOpenVersion = { poemId, createdAt -> navigateTo(Route.History(poemId, createdAt)) },
        )

        is Route.History -> HistoryPage(
            poemId = route.poemId,
            createdAt = route.createdAt,
            onBack = ::onBack,
        )

        Route.Search -> SearchPage(
            onBack = ::onBack,
            onOpenPoem = { poem -> navigateTo(Route.Book(poem.bookId, poem.id)) },
        )

        Route.Settings -> SettingsPage(
            onBack = ::onBack,
            onOpenBin = { navigateTo(Route.Bin) },
            // Debug builds only: release has no way to reach the debug pages.
            onOpenDebug = if (BuildConfig.DEBUG) ({ navigateTo(Route.Debug) }) else null,
        )

        Route.Bin -> BinPage(
            onBack = ::onBack,
            onOpenBook = { navigateTo(Route.BinBook(it)) },
            onOpenPoem = { navigateTo(Route.BinPoem(it)) },
        )

        is Route.BinPoem -> BinPoemPage(route.poemId, onBack = ::onBack)

        is Route.BinBook -> BinBookPage(route.bookId, onBack = ::onBack)

        Route.Debug -> DebugPage(
            onBack = ::onBack,
            onOpenLogs = { navigateTo(Route.Logs) },
            onOpenCrashes = { navigateTo(Route.Crashes) },
        )

        Route.Logs -> LogsPage(onBack = ::onBack)

        Route.Crashes -> CrashesPage(onBack = ::onBack)
    }
}
