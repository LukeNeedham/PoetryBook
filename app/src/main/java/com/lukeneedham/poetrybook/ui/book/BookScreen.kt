package com.lukeneedham.poetrybook.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.pageturn.PageTurnBook
import com.lukeneedham.poetrybook.pageturn.rememberPageTurnState
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * One book of poems, which you swipe or tap through, with a contents sheet dropping from the top.
 * Its first page is a generated contents page. The book always ends with a blank poem, ready to be written.
 */
@Composable
fun BookScreen(
    viewModel: PoemBookViewModel,
    onOpenShelf: () -> Unit,
    onOpenVersion: (poemId: String, createdAt: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val poems = viewModel.poems
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var notepadOpen by rememberSaveable { mutableStateOf(false) }
    var bodyFocusSignal by rememberSaveable { mutableIntStateOf(0) }
    var shownBook by rememberSaveable { mutableStateOf(viewModel.currentBook?.id) }
    val focusManager = LocalFocusManager.current

    // Page 0 is the generated contents page; the poems follow it.
    fun poemAt(page: Int) = poems.getOrNull(page - 1)

    // Finishing an edit saves the poem as a new version, if anything changed.
    fun stopEditing() {
        if (editing) poemAt(pageState.currentPage)?.let { viewModel.commit(it.id) }
        focusManager.clearFocus()
        editing = false
    }

    // A different book starts from its first page.
    LaunchedEffect(viewModel.currentBook?.id) {
        val book = viewModel.currentBook?.id
        if (book != null && book != shownBook) {
            shownBook = book
            editing = false
            historyOpen = false
            notepadOpen = false
            pageState.snapTo(0)
        }
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
                // Nothing to delete from a blank poem, such as the one at the end of the book. This
                // follows the current page, which only changes when a turn has settled, so the icon
                // keeps its look while pages turn instead of flashing.
                canDelete = currentPoem != null && !currentPoem.isBlank,
                canEdit = currentPoem != null,
                // Only a poem that has been saved has a history and a notepad.
                hasPoem = currentPoem != null && viewModel.isStored(currentPoem.id),
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
                // Mid-turn the poem on show is about to change, so wait for the page to settle.
                onDelete = { if (!pageState.isTurning) confirmingDelete = true },
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
                    onTitleChange = { viewModel.updateTitle(poem.id, it) },
                    onBodyChange = { viewModel.updateBody(poem.id, it) },
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
            onMove = { from, to ->
                // The page on show follows its poem to wherever that goes.
                val shownId = poemAt(pageState.currentPage)?.id
                viewModel.movePoem(from, to)
                val shownIndex = poems.indexOfFirst { it.id == shownId } + 1
                if (shownIndex > 0 && shownIndex != pageState.currentPage) pageState.snapTo(shownIndex)
            },
            onDismiss = { contentsOpen = false },
        )
    }

    val historyPoem = poemAt(pageState.currentPage)
    if (historyOpen && historyPoem != null) {
        HistorySheet(
            versions = remember(historyPoem.id) { viewModel.history(historyPoem.id) },
            onSelect = { version ->
                historyOpen = false
                onOpenVersion(historyPoem.id, version.createdAt)
            },
            onDismiss = { historyOpen = false },
        )
    }

    // Read the notes of the poem on show as soon as it is stored, so the notepad opens ready.
    val notepadPoem = poemAt(pageState.currentPage)
    val notepadPoemId = notepadPoem?.id?.takeIf { viewModel.isStored(it) }
    LaunchedEffect(notepadPoemId) { notepadPoemId?.let { viewModel.loadNotes(it) } }
    val notepadNotes = notepadPoemId?.let { viewModel.loadedNotes(it) }
    if (notepadOpen && notepadPoemId != null && notepadNotes != null) {
        NotepadSheet(
            viewModel = viewModel,
            poemId = notepadPoemId,
            notes = notepadNotes,
            onDismiss = {
                notepadOpen = false
                // Back to typing in the poem, if that's what was going on.
                if (editing) bodyFocusSignal++
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
                        pageState.snapTo(viewModel.deletePoem(poemToDelete.id) + 1)
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
