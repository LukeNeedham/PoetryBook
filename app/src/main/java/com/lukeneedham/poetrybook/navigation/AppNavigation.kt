package com.lukeneedham.poetrybook.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.lukeneedham.poetrybook.ui.theme.paperColors

@Composable
fun AppNavigation(viewModel: PoemBookViewModel) {
    when (viewModel.status) {
        // The start screen depends on whether a poems folder is already set up.
        BookStatus.LOADING -> Box(Modifier.fillMaxSize().background(paperColors().paper))
        BookStatus.NEEDS_FOLDER -> AppNavDisplay(viewModel, start = Onboarding)
        BookStatus.READY -> AppNavDisplay(viewModel, start = Book)
    }
}

@Composable
private fun AppNavDisplay(viewModel: PoemBookViewModel, start: NavKey) {
    val backStack = rememberNavBackStack(start)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Onboarding> {
                OnboardingScreen(
                    folderLost = viewModel.folderLost,
                    onFolderPicked = { uri, onDone ->
                        viewModel.chooseFolder(uri) { ok ->
                            onDone(ok)
                            if (ok) {
                                backStack.clear()
                                backStack.add(Book)
                            }
                        }
                    },
                )
            }
            entry<Book> {
                BookScreen(
                    viewModel = viewModel,
                    onOpenSettings = { backStack.add(Settings) },
                )
            }
            entry<Settings> {
                SettingsScreen(
                    folderUri = viewModel.folderUri,
                    onBack = { backStack.removeLastOrNull() },
                    onFolderPicked = { uri, onDone -> viewModel.chooseFolder(uri, onDone) },
                )
            }
        },
    )
}
