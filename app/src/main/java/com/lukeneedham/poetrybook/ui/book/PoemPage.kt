package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDelete: () -> Unit,
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
        var confirmingDelete by remember { mutableStateOf(false) }

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
                        lineLimits = TextFieldLineLimits.SingleLine,
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    BasicTextField(
                        state = bodyState,
                        textStyle = bodyStyle,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    // Nothing to delete until something is written.
                    if (!poem.isBlank) TextButton(onClick = { confirmingDelete = true }) {
                        Text(
                            text = stringResource(R.string.delete_poem),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        text = pageNumber.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = PoemFont),
                        color = colors.faintInk,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }

        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text(stringResource(R.string.delete_poem_title)) },
                text = { Text(stringResource(R.string.delete_poem_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmingDelete = false
                            onDelete()
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
            onDelete = {},
        )
    }
}
