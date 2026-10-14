package com.lukeneedham.poetrybook.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lukeneedham.poetrybook.ui.book.BookScreen
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel

@Composable
fun AppNavigation(viewModel: PoemBookViewModel) {
    val backStack = rememberNavBackStack(Book)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Book> {
                BookScreen(viewModel)
            }
        },
    )
}
