package com.lukeneedham.poetrybook.ui.book

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lukeneedham.poetrybook.ui.appViewModel
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.ui.shelf.BookCoverFace
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.HistoryList
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.pageturn.PageTurnBook
import com.lukeneedham.poetrybook.pageturn.rememberPageTurnState
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * One book of poems, which you swipe or tap through, with a contents sheet dropping from the top.
 * Its first page is a generated contents page. The book always ends with a blank poem, ready to be written.
 *
 * @param bookId the book to read.
 * @param poemToShow a poem of the book to open on, once, or null.
 * @param onBack leaves the page, if the book can't be read (it is gone, e.g. after the app was killed).
 */
@Composable
fun BookPage(
    bookId: String,
    poemToShow: String?,
    onBack: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenVersion: (poemId: String, createdAt: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { BookViewModel(it, bookId, poemToShow) }

    LaunchedEffect(viewModel.loadFailed) { if (viewModel.loadFailed) onBack() }
    // Finished edits are saved when the app goes to the background
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.commitAll() }

    // Nothing is drawn until the poems have been read, which is quick
    if (!viewModel.isLoaded) return

    BookPageContent(
        poems = viewModel.poems,
        currentBookId = bookId,
        shelfBooks = viewModel.shelfBooks,
        poemToShow = viewModel.poemToShow,
        onPoemShown = viewModel::poemShown,
        isStored = viewModel::isStored,
        loadedHistory = viewModel::loadedHistory,
        loadedNotes = viewModel::loadedNotes,
        trackHistory = viewModel::trackHistory,
        loadNotes = viewModel::loadNotes,
        onLoadMoreHistory = viewModel::loadMoreHistory,
        onSaveNotes = viewModel::saveNotes,
        onCommit = viewModel::commit,
        onTitleChange = viewModel::updateTitle,
        onBodyChange = viewModel::updateBody,
        onMovePoem = viewModel::movePoem,
        onMovePoemToBook = viewModel::movePoemToBook,
        onDeletePoem = viewModel::deletePoem,
        onOpenShelf = onOpenShelf,
        onOpenVersion = { poemId, createdAt ->
            // The history page reads the poem as it is saved, so finish the edit first
            viewModel.commitAll()
            onOpenVersion(poemId, createdAt)
        },
        modifier = modifier,
    )
}
