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
import androidx.compose.runtime.mutableStateOf
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
 * The book always ends with a blank poem, ready to be written.
 */
@Composable
fun BookScreen(
    viewModel: PoemBookViewModel,
    onOpenShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poems = viewModel.poems
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var shownBook by rememberSaveable { mutableStateOf(viewModel.currentBook?.documentId) }
    val focusManager = LocalFocusManager.current

    fun stopEditing() {
        focusManager.clearFocus()
        editing = false
    }

    // A different book starts from its first page.
    LaunchedEffect(viewModel.currentBook?.documentId) {
        val book = viewModel.currentBook?.documentId
        if (book != null && book != shownBook) {
            shownBook = book
            editing = false
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
            val currentPoem = poems.getOrNull(pageState.currentPage)
            BookToolbar(
                editing = editing,
                // Nothing to delete from a blank poem, such as the one at the end of the book. This
                // follows the current page, which only changes when a turn has settled, so the icon
                // keeps its look while pages turn instead of flashing.
                canDelete = currentPoem != null && !currentPoem.isBlank,
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
                    if (editing) stopEditing() else if (!pageState.isTurning) editing = true
                },
            )

            // The pages turn only in the space below the toolbar.
            PageTurnBook(
                state = pageState,
                pageCount = poems.size,
                gesturesEnabled = !editing && !contentsOpen,
                // Tapping the middle of the unstarted poem at the end starts writing in it.
                onCenterTap = {
                    val page = poems.getOrNull(pageState.currentPage)
                    if (page != null && page.isBlank && !pageState.isTurning) editing = true
                },
                modifier = Modifier.weight(1f),
            ) { index ->
                val poem = poems.getOrNull(index) ?: return@PageTurnBook
                PoemPage(
                    poem = poem,
                    pageNumber = index + 1,
                    editing = editing && index == pageState.currentPage,
                    onTitleChange = { viewModel.updateTitle(poem.id, it) },
                    onBodyChange = { viewModel.updateBody(poem.id, it) },
                )
            }
        }

        ContentsSheet(
            visible = contentsOpen,
            poems = poems,
            currentIndex = pageState.currentPage,
            onSelect = { index ->
                contentsOpen = false
                pageState.animateTo(index)
            },
            onMove = { from, to ->
                // The page on show follows its poem to wherever that goes.
                val shownId = poems.getOrNull(pageState.currentPage)?.id
                viewModel.movePoem(from, to)
                val shownIndex = poems.indexOfFirst { it.id == shownId }
                if (shownIndex >= 0 && shownIndex != pageState.currentPage) pageState.snapTo(shownIndex)
            },
            onDismiss = { contentsOpen = false },
        )
    }

    val poemToDelete = poems.getOrNull(pageState.currentPage)
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
                        pageState.snapTo(viewModel.deletePoem(poemToDelete.id))
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
