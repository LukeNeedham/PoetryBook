package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * One page of the book: a poem with its title centred at the top. The book's toolbar sits above
 * the page and isn't part of it, so it doesn't turn. It reads as plain paper until [editing]
 * is true, when the title and text become editable.
 */
@Composable
fun PoemPage(
    poem: Poem,
    pageNumber: Int,
    editing: Boolean,
    focusSignal: Int = 0,
    onDelete: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    // The text fields hold their own state while editing; a different poem needs fresh state.
    key(poem.id) {
        val titleState = rememberTextFieldState(poem.title)
        val bodyState = rememberTextFieldState(poem.body)
        LaunchedEffect(titleState) {
            snapshotFlow { titleState.text.toString() }.collect(onTitleChange)
        }
        LaunchedEffect(bodyState) {
            snapshotFlow { bodyState.text.toString() }.collect(onBodyChange)
        }

        // Reading and editing share one scroll position, so switching between them doesn't move the text.
        val bodyScroll = rememberScrollState()
        // How the text was laid out when reading, to find the line at the top of the screen.
        var readingLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

        val titleStyle = TextStyle(
            fontFamily = PoemFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center,
            color = colors.ink,
        )
        val bodyStyle = TextStyle(
            fontFamily = PoemFont,
            fontSize = 19.sp,
            lineHeight = 30.sp,
            color = colors.ink,
        )
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.paper)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (editing) {
                    BasicTextField(
                        state = titleState,
                        textStyle = titleStyle,
                        // Long titles wrap rather than scroll, and Enter starts a new line.
                        cursorBrush = SolidColor(colors.accent),
                        decorator = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (titleState.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.title_placeholder),
                                        style = titleStyle.copy(
                                            color = colors.faintInk,
                                            fontWeight = FontWeight.Normal,
                                        ),
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = poem.title.ifBlank { stringResource(R.string.untitled) },
                        style = titleStyle.let {
                            if (poem.title.isBlank()) {
                                it.copy(
                                    color = colors.faintInk,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                if (editing) {
                    // Start typing at the end of the first line fully on screen, so the cursor is already
                    // in view and nothing needs to scroll.
                    LaunchedEffect(Unit) {
                        val offset = readingLayout?.let { layout ->
                            val top = bodyScroll.value
                            val line = (0 until layout.lineCount).firstOrNull {
                                layout.getLineTop(it) >= top - 0.5f && layout.getLineBottom(it) > layout.getLineTop(it)
                            } ?: (layout.lineCount - 1)
                            layout.getLineEnd(line.coerceAtLeast(0), visibleEnd = true)
                        } ?: 0
                        bodyState.edit { selection = TextRange(offset.coerceIn(0, length)) }
                    }
                    // Whatever the text field does to show the cursor, the text stays where it was while
                    // the editor settles in.
                    LaunchedEffect(Unit) {
                        val target = bodyScroll.value
                        withTimeoutOrNull(500) {
                            snapshotFlow { bodyScroll.value }.collect { if (it != target) bodyScroll.scrollTo(target) }
                        }
                    }
                    val focusRequester = remember { FocusRequester() }
                    // Also when the signal changes, to take focus back after something else, like the notepad, had it.
                    LaunchedEffect(focusSignal) { focusRequester.requestFocus() }
                    BasicTextField(
                        state = bodyState,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        textStyle = bodyStyle,
                        scrollState = bodyScroll,
                        cursorBrush = SolidColor(colors.accent),
                        decorator = { inner ->
                            Box {
                                if (bodyState.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.body_placeholder),
                                        style = bodyStyle.copy(color = colors.faintInk),
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    )
                } else if (poem.body.isBlank()) {
                    Text(
                        text = stringResource(R.string.empty_poem_hint),
                        style = bodyStyle.copy(color = colors.faintInk, fontStyle = FontStyle.Italic),
                    )
                } else {
                    Text(
                        text = poem.body,
                        style = bodyStyle,
                        onTextLayout = { readingLayout = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(bodyScroll),
                    )
                }
            }

            if (editing) {
                // Actions on the poem belong to editing, and sit here so the top toolbar never changes.
                if (onDelete != null || onMove != null) {
                    EditToolbar(onDelete = onDelete, onMove = onMove)
                }
            } else {
                Text(
                    text = pageNumber.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = PoemFont),
                    color = colors.faintInk,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 20.dp),
                )
            }
        }
    }
}

/** The second toolbar, along the bottom while a poem is being edited: actions on the poem itself. */
@Composable
private fun EditToolbar(onDelete: (() -> Unit)?, onMove: (() -> Unit)?) {
    val colors = paperColors()
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = stringResource(R.string.delete_poem),
                        tint = colors.accent,
                    )
                }
            }
            if (onMove != null) {
                IconButton(onClick = onMove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_move),
                        contentDescription = stringResource(R.string.move_poem),
                        tint = colors.accent,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun PoemPagePreview() {
    PoetryBookTheme {
        PoemPage(
            poem = Poem("1", "Evening", "The light goes out\nof the afternoon,\nslowly, like a breath."),
            pageNumber = 1,
            editing = false,
            onTitleChange = {},
            onBodyChange = {},
        )
    }
}
