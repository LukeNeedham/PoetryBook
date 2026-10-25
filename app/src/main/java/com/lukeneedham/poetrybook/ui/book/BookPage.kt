package com.lukeneedham.poetrybook.ui.book

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
 */
@Composable
fun BookPage(
    viewModel: PoemBookViewModel,
    onOpenShelf: () -> Unit,
    onOpenVersion: (poemId: String, createdAt: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookPageContent(
        poems = viewModel.poems,
        currentBookId = viewModel.currentBook?.id,
        shelfBooks = viewModel.shelfBooks,
        poemToShow = viewModel.poemToShow,
        onPoemShown = { viewModel.poemToShow = null },
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
        onOpenVersion = onOpenVersion,
        modifier = modifier,
    )
}

/**
 * [poems] are the open book's poems, ending in the blank one. [poemToShow] is a poem to turn to once,
 * which [onPoemShown] clears. [onMovePoemToBook] and [onDeletePoem] return the index of the page to show instead.
 */
@Composable
internal fun BookPageContent(
    poems: List<Poem>,
    currentBookId: String?,
    shelfBooks: List<Book>,
    poemToShow: String?,
    onPoemShown: () -> Unit,
    isStored: (String) -> Boolean,
    loadedHistory: (String) -> HistoryList?,
    loadedNotes: (String) -> String?,
    trackHistory: suspend (String) -> Unit,
    loadNotes: suspend (String) -> Unit,
    onLoadMoreHistory: (poemId: String, count: Int) -> Unit,
    onSaveNotes: (poemId: String, notes: String) -> Unit,
    onCommit: (String) -> Unit,
    onTitleChange: (poemId: String, title: String) -> Unit,
    onBodyChange: (poemId: String, body: String) -> Unit,
    onMovePoem: (from: Int, to: Int) -> Unit,
    onMovePoemToBook: (poemId: String, bookId: String) -> Int,
    onDeletePoem: (poemId: String) -> Int,
    onOpenShelf: () -> Unit,
    onOpenVersion: (poemId: String, createdAt: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var choosingBook by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var notepadOpen by rememberSaveable { mutableStateOf(false) }
    var bodyFocusSignal by rememberSaveable { mutableIntStateOf(0) }
    var shownBook by rememberSaveable { mutableStateOf(currentBookId) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Page 0 is the generated contents page; the poems follow it.
    fun poemAt(page: Int) = poems.getOrNull(page - 1)

    // Finishing an edit saves the poem as a new version, if anything changed.
    fun stopEditing() {
        if (editing) poemAt(pageState.currentPage)?.let { onCommit(it.id) }
        focusManager.clearFocus()
        editing = false
    }

    // A different book starts from its first page.
    LaunchedEffect(currentBookId) {
        val book = currentBookId
        if (book != null && book != shownBook) {
            shownBook = book
            editing = false
            historyOpen = false
            notepadOpen = false
            pageState.snapTo(0)
        }
    }

    // A search result opens its book on the poem it found.
    LaunchedEffect(poemToShow, poems.size) {
        if (poemToShow == null) return@LaunchedEffect
        val index = poems.indexOfFirst { it.id == poemToShow }
        if (index >= 0) pageState.snapTo(index + 1)
        onPoemShown()
    }

    BackHandler(enabled = editing) { stopEditing() }

    Box(modifier = modifier.fillMaxSize().background(paperColors().paper)) {
        // The status bar and sides are dealt with here, once, so the toolbar sits below the status
        // bar and the pages (which only add what's left, e.g. the keyboard) sit below the toolbar.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        ) {
            val currentPoem = poemAt(pageState.currentPage)
            BookToolbar(
                editing = editing,
                canEdit = currentPoem != null,
                // Only a poem that has been saved has a history and a notepad.
                hasPoem = currentPoem != null && isStored(currentPoem.id),
                canShare = currentPoem != null && !currentPoem.isBlank,
                onShare = { currentPoem?.let { sharePoem(context, it) } },
                onOpenHistory = {
                    if (!pageState.isTurning) {
                        stopEditing()
                        historyOpen = true
                    }
                },
                onOpenNotepad = {
                    // Editing carries on underneath the notepad.
                    if (!pageState.isTurning) {
                        notepadOpen = true
                    }
                },
                onOpenShelf = {
                    stopEditing()
                    onOpenShelf()
                },
                onOpenContents = {
                    stopEditing()
                    contentsOpen = true
                },
                onToggleEditing = {
                    if (editing) stopEditing() else if (!pageState.isTurning && currentPoem != null) editing = true
                },
            )

            // The pages turn only in the space below the toolbar.
            PageTurnBook(
                state = pageState,
                pageCount = poems.size + 1,
                gesturesEnabled = !editing && !contentsOpen,
                // Tapping the middle of the unstarted poem at the end starts writing in it.
                onCenterTap = {
                    val page = poemAt(pageState.currentPage)
                    if (page != null && page.isBlank && !pageState.isTurning) editing = true
                },
                modifier = Modifier.weight(1f),
            ) { index ->
                if (index == 0) {
                    ContentsPage(
                        poems = poems,
                        onSelect = { poemIndex ->
                            if (!pageState.isTurning) pageState.animateTo(poemIndex + 1)
                        },
                    )
                    return@PageTurnBook
                }
                val poem = poems.getOrNull(index - 1) ?: return@PageTurnBook
                PoemPage(
                    poem = poem,
                    pageNumber = index,
                    editing = editing && index == pageState.currentPage,
                    focusSignal = bodyFocusSignal,
                    // Nothing to delete from a blank poem, such as the one at the end of the book.
                    onDelete = if (poem.isBlank) null else {
                        { if (!pageState.isTurning) confirmingDelete = true }
                    },
                    onMove = if (poem.isBlank) null else {
                        { if (!pageState.isTurning) choosingBook = true }
                    },
                    onTitleChange = { onTitleChange(poem.id, it) },
                    onBodyChange = { onBodyChange(poem.id, it) },
                )
            }
        }

        ContentsSheet(
            visible = contentsOpen,
            poems = poems,
            currentIndex = pageState.currentPage - 1,
            onSelect = { index ->
                contentsOpen = false
                pageState.animateTo(index + 1)
            },
            onSelectContentsPage = {
                contentsOpen = false
                pageState.animateTo(0)
            },
            onMove = { from, to ->
                // The page on show follows its poem to wherever that goes.
                val shownId = poemAt(pageState.currentPage)?.id
                onMovePoem(from, to)
                val shownIndex = poems.indexOfFirst { it.id == shownId } + 1
                if (shownIndex > 0 && shownIndex != pageState.currentPage) pageState.snapTo(shownIndex)
            },
            onDismiss = { contentsOpen = false },
        )
    }

    // Work out the history of the poem on show as soon as it is stored, so the sheet opens ready.
    val historyPoem = poemAt(pageState.currentPage)
    val historyPoemId = historyPoem?.id?.takeIf { isStored(it) }
    LaunchedEffect(historyPoemId) { historyPoemId?.let { trackHistory(it) } }

    if (historyOpen && historyPoem != null) {
        HistorySheet(
            history = loadedHistory(historyPoem.id),
            onLoadMore = { count -> onLoadMoreHistory(historyPoem.id, count) },
            onSelect = { version ->
                historyOpen = false
                onOpenVersion(historyPoem.id, version.createdAt)
            },
            onDismiss = { historyOpen = false },
        )
    }

    // Read the notes of the poem on show as soon as it is stored, so the notepad opens ready.
    val notepadPoem = poemAt(pageState.currentPage)
    val notepadPoemId = notepadPoem?.id?.takeIf { isStored(it) }
    LaunchedEffect(notepadPoemId) { notepadPoemId?.let { loadNotes(it) } }
    val notepadNotes = notepadPoemId?.let { loadedNotes(it) }
    if (notepadOpen && notepadPoemId != null && notepadNotes != null) {
        NotepadSheet(
            onSaveNotes = onSaveNotes,
            poemId = notepadPoemId,
            notes = notepadNotes,
            onDismiss = {
                notepadOpen = false
                // Back to typing in the poem, if that's what was going on.
                if (editing) bodyFocusSignal++
            },
        )
    }

    val poemToMove = poemAt(pageState.currentPage)
    if (choosingBook && poemToMove != null) {
        val otherBooks = shelfBooks.filter { it.id != currentBookId }
        AlertDialog(
            onDismissRequest = { choosingBook = false },
            title = { Text(stringResource(R.string.move_poem_title)) },
            text = {
                if (otherBooks.isEmpty()) {
                    Text(stringResource(R.string.move_poem_no_books))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(otherBooks, key = { it.id }) { book ->
                            BookCoverFace(
                                book = book,
                                showCount = false,
                                modifier = Modifier.clickable {
                                    choosingBook = false
                                    stopEditing()
                                    pageState.snapTo(onMovePoemToBook(poemToMove.id, book.id) + 1)
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingBook = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val poemToDelete = poemAt(pageState.currentPage)
    if (confirmingDelete && poemToDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_poem_title)) },
            text = { Text(stringResource(R.string.delete_poem_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        stopEditing()
                        pageState.snapTo(onDeletePoem(poemToDelete.id) + 1)
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Opens the share sheet with the poem's title and full text. */
private fun sharePoem(context: Context, poem: Poem) {
    val text = listOf(poem.title.trim(), poem.body.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (poem.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, poem.title.trim())
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share_poem)))
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    PoetryBookTheme {
        BookPageContent(
            poems = listOf(
                Poem("1", "Evening", "The light goes slowly\nover the roofs"),
                Poem("2", "Frost", "Silver on the grass"),
                Poem("3"),
            ),
            currentBookId = "a",
            shelfBooks = listOf(Book("a", "Love poems", 2), Book("b", "Winter", 1)),
            poemToShow = null,
            onPoemShown = {},
            isStored = { true },
            loadedHistory = { null },
            loadedNotes = { null },
            trackHistory = {},
            loadNotes = {},
            onLoadMoreHistory = { _, _ -> },
            onSaveNotes = { _, _ -> },
            onCommit = {},
            onTitleChange = { _, _ -> },
            onBodyChange = { _, _ -> },
            onMovePoem = { _, _ -> },
            onMovePoemToBook = { _, _ -> 0 },
            onDeletePoem = { 0 },
            onOpenShelf = {},
            onOpenVersion = { _, _ -> },
        )
    }
}
