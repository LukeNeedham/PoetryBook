package com.lukeneedham.poetrybook.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.poetrybook.ui.book.BookScreen
import com.lukeneedham.poetrybook.ui.book.BookStatus
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.onboarding.OnboardingScreen
import com.lukeneedham.poetrybook.ui.settings.SettingsScreen
import com.lukeneedham.poetrybook.ui.shelf.ShelfScreen
import com.lukeneedham.poetrybook.ui.theme.paperColors

private const val ScreenTransitionMillis = 300

/** Going back: the screen being left slides away to the right, on top of the one revealed. */
private fun popTransition(): ContentTransform =
    (
        slideInHorizontally(tween(ScreenTransitionMillis)) { -it / 3 } togetherWith
            slideOutHorizontally(tween(ScreenTransitionMillis)) { it }
        ).apply { targetContentZIndex = -1f }

@Composable
fun AppNavigation(viewModel: PoemBookViewModel) {
    when (viewModel.status) {
        // The start screen depends on whether a poems folder is already set up.
        BookStatus.LOADING -> Box(Modifier.fillMaxSize().background(paperColors().paper))
        BookStatus.NEEDS_FOLDER -> AppNavDisplay(viewModel, start = Onboarding)
        BookStatus.READY -> AppNavDisplay(viewModel, start = Shelf)
    }
}

@Composable
private fun AppNavDisplay(viewModel: PoemBookViewModel, start: NavKey) {
    val backStack = rememberNavBackStack(start)

    NavDisplay(
        // Paper behind the screens, so a transition never shows anything else through a gap.
        modifier = Modifier.background(paperColors().paper),
        backStack = backStack,
        // Screens slide over one another. They're all opaque paper, so unlike a crossfade nothing
        // is ever see-through (a crossfade over the white window background flashes in dark mode).
        transitionSpec = {
            slideInHorizontally(tween(ScreenTransitionMillis)) { it } togetherWith
                slideOutHorizontally(tween(ScreenTransitionMillis)) { -it / 3 }
        },
        popTransitionSpec = { popTransition() },
        predictivePopTransitionSpec = { popTransition() },
        onBack = {
            // Leaving a book by going back puts it on the shelf, like the shelf button does.
            if (backStack.lastOrNull() == Book) viewModel.closeBook()
            backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<Onboarding> {
                OnboardingScreen(
                    folderLost = viewModel.folderLost,
                    onFolderPicked = { uri, onDone ->
                        viewModel.chooseFolder(uri) { ok ->
                            onDone(ok)
                            if (ok) {
                                backStack.clear()
                                backStack.add(Shelf)
                            }
                        }
                    },
                )
            }
            entry<Shelf> {
                LaunchedEffect(Unit) { viewModel.refreshBooks() }
                ShelfScreen(
                    books = viewModel.shelfBooks,
                    sort = viewModel.bookSort,
                    onSortChange = viewModel::sortBooks,
                    onOpenBook = { book ->
                        viewModel.openBook(book) { ok -> if (ok) backStack.add(Book) }
                    },
                    onCreateBook = { name, color, onDone -> viewModel.createBook(name, color) { onDone(it != null) } },
                    onEditBook = { book, name, color, onDone -> viewModel.editBook(book, name, color, onDone) },
                    onDeleteBook = { book, onDone -> viewModel.deleteBook(book, onDone) },
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<Book> {
                // After the app was killed the back stack comes back but the open book doesn't.
                if (viewModel.currentBook == null) {
                    LaunchedEffect(Unit) {
                        if (backStack.lastOrNull() == Book) backStack.removeLastOrNull()
                    }
                    return@entry
                }
                BookScreen(
                    viewModel = viewModel,
                    onOpenShelf = {
                        viewModel.closeBook()
                        backStack.removeLastOrNull()
                    },
                )
            }
            entry<Settings> {
                SettingsScreen(
                    folderUri = viewModel.folderUri,
                    onBack = { backStack.removeLastOrNull() },
                    onFolderPicked = { uri, onDone ->
                        viewModel.chooseFolder(uri) { ok ->
                            onDone(ok)
                            if (ok) {
                                // The old folder's books are gone from the shelf; start from the new one.
                                backStack.clear()
                                backStack.add(Shelf)
                            }
                        }
                    },
                )
            }
        },
    )
}
