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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poems = viewModel.poems
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
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
            BookToolbar(
                editing = editing,
                onOpenContents = {
                    stopEditing()
                    contentsOpen = true
                },
                onToggleEditing = {
                    if (editing) stopEditing() else if (!pageState.isTurning) editing = true
                },
            )

            // The pages turn only in the space below the toolbar.
            PageTurnBook(
                state = pageState,
                pageCount = poems.size,
                gesturesEnabled = !editing && !contentsOpen,
                modifier = Modifier.weight(1f),
            ) { index ->
                val poem = poems.getOrNull(index) ?: return@PageTurnBook
                PoemPage(
                    poem = poem,
                    pageNumber = index + 1,
                    editing = editing && index == pageState.currentPage,
                    onTitleChange = { viewModel.updateTitle(poem.id, it) },
                    onBodyChange = { viewModel.updateBody(poem.id, it) },
                    onDelete = {
                        stopEditing()
                        pageState.snapTo(viewModel.deletePoem(poem.id))
                    },
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
            onOpenShelf = {
                contentsOpen = false
                stopEditing()
                onOpenShelf()
            },
            onOpenSettings = {
                contentsOpen = false
                onOpenSettings()
            },
            onDismiss = { contentsOpen = false },
        )
    }
}
