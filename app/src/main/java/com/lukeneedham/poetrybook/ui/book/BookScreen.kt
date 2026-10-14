package com.lukeneedham.poetrybook.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.lukeneedham.poetrybook.pageturn.PageTurnBook
import com.lukeneedham.poetrybook.pageturn.rememberPageTurnState
import kotlinx.coroutines.launch

/**
 * The whole app: a book of poems you swipe through, with a contents sheet dropping from the top.
 */
@Composable
fun BookScreen(
    viewModel: PoemBookViewModel,
    modifier: Modifier = Modifier,
) {
    val poems = viewModel.poems
    val pageState = rememberPageTurnState()
    var editing by rememberSaveable { mutableStateOf(false) }
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun stopEditing() {
        focusManager.clearFocus()
        editing = false
    }

    BackHandler(enabled = editing) { stopEditing() }

    Box(modifier = modifier.fillMaxSize()) {
        PageTurnBook(
            state = pageState,
            pageCount = poems.size,
            gesturesEnabled = !editing && !contentsOpen,
        ) { index ->
            val poem = poems.getOrNull(index) ?: return@PageTurnBook
            PoemPage(
                poem = poem,
                pageNumber = index + 1,
                editing = editing && index == pageState.currentPage,
                onOpenContents = {
                    stopEditing()
                    contentsOpen = true
                },
                onStartEditing = { editing = true },
                onStopEditing = ::stopEditing,
                onTitleChange = { viewModel.updateTitle(poem.id, it) },
                onBodyChange = { viewModel.updateBody(poem.id, it) },
                onDelete = {
                    stopEditing()
                    pageState.snapTo(viewModel.deletePoem(poem.id))
                },
            )
        }

        ContentsSheet(
            visible = contentsOpen,
            poems = poems,
            currentIndex = pageState.currentPage,
            onSelect = { index ->
                contentsOpen = false
                pageState.animateTo(index)
            },
            onNewPoem = {
                contentsOpen = false
                val index = viewModel.addPoem()
                scope.launch {
                    // Let the book see the new page before turning to it.
                    withFrameNanos { }
                    pageState.animateTo(index)
                }
            },
            onDismiss = { contentsOpen = false },
        )
    }
}
