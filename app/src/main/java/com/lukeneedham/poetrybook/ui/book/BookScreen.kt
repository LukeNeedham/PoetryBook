package com.lukeneedham.poetrybook.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.lukeneedham.poetrybook.pageturn.PageTurnBook
import com.lukeneedham.poetrybook.pageturn.rememberPageTurnState

/**
 * The whole app: a book of poems you swipe or tap through, with a contents sheet dropping from
 * the top. The book's last page is always the one for starting a new poem.
 */
@Composable
fun BookScreen(
    viewModel: PoemBookViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val poems = viewModel.poems
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    var shownFolder by rememberSaveable { mutableStateOf(viewModel.folderUri?.toString()) }
    val focusManager = LocalFocusManager.current

    fun stopEditing() {
        focusManager.clearFocus()
        editing = false
    }

    // A different folder is a different book: start it from the beginning.
    LaunchedEffect(viewModel.folderUri) {
        val folder = viewModel.folderUri?.toString()
        if (folder != shownFolder) {
            shownFolder = folder
            editing = false
            pageState.snapTo(0)
        }
    }

    BackHandler(enabled = editing) { stopEditing() }

    // The poems, then one page for adding another.
    val newPoemPage = poems.size
    val onPoem = pageState.currentPage < newPoemPage

    Box(modifier = modifier.fillMaxSize()) {
        PageTurnBook(
            state = pageState,
            pageCount = poems.size + 1,
            gesturesEnabled = !editing && !contentsOpen,
        ) { index ->
            val poem = poems.getOrNull(index)
            if (poem == null) {
                NewPoemPage(
                    onNewPoem = {
                        // The new poem takes over this page; a fresh page for adding one follows it.
                        viewModel.addPoem()
                        editing = true
                    },
                )
            } else {
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

        BookToolbar(
            canEdit = onPoem,
            editing = editing,
            onOpenContents = {
                stopEditing()
                contentsOpen = true
            },
            onToggleEditing = {
                if (editing) stopEditing() else if (!pageState.isTurning) editing = true
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ContentsSheet(
            visible = contentsOpen,
            poems = poems,
            currentIndex = pageState.currentPage,
            onSelect = { index ->
                contentsOpen = false
                pageState.animateTo(index)
            },
            onOpenSettings = {
                contentsOpen = false
                onOpenSettings()
            },
            onDismiss = { contentsOpen = false },
        )
    }
}
