package com.lukeneedham.poetrybook.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.BookSort
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.TitleFont
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/** Stands for the book being created in [ShelfScreen]'s `editing` state; no real document id is empty. */
private const val NewBook = ""

/**
 * The bookshelf: every book in the chosen folder as a cover. Tap one to read it.
 *
 * The last tile starts a new book: its cover becomes editable right there, name and colour. The
 * long press on a cover does the same for an existing book, which stays where it is.
 *
 * @param sort how [books] are ordered, which the caller does; [onSortChange] is called when the user picks another.
 * @param onCreateBook creates a book with the given name and cover colour (ARGB); reports whether that worked.
 * @param onEditBook renames a book and gives it a cover colour (ARGB); reports whether that worked.
 */
@Composable
fun ShelfScreen(
    books: List<Book>,
    sort: BookSort,
    onSortChange: (BookSort) -> Unit,
    onOpenBook: (Book) -> Unit,
    onCreateBook: (name: String, color: Int, onDone: (Boolean) -> Unit) -> Unit,
    onEditBook: (book: Book, name: String, color: Int, onDone: (Boolean) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    // The document id of the book being edited, NewBook for the one being created, or null.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Bring the book being created into view; it's the last tile.
    LaunchedEffect(editing) {
        if (editing == NewBook) gridState.animateScrollToItem(books.size)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bookshelf),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TitleFont),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            SortMenu(sort = sort, onSortChange = onSortChange)
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = colors.accent,
                )
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(books, key = { it.documentId }) { book ->
                if (editing == book.documentId) {
                    BookEditor(
                        initialName = book.name,
                        viewportHeight = { gridState.layoutInfo.viewportSize.height },
                        initialColor = coverColor(book),
                        nameEditable = !book.isLooseFiles,
                        failed = failed,
                        onConfirm = { name, color, onDone ->
                            failed = false
                            onEditBook(book, name, color.toArgb()) { ok ->
                                if (ok) editing = null else failed = true
                                onDone()
                            }
                        },
                        onCancel = {
                            failed = false
                            editing = null
                        },
                    )
                } else {
                    BookCover(
                        book = book,
                        onClick = { onOpenBook(book) },
                        onEdit = {
                            failed = false
                            editing = book.documentId
                        },
                    )
                }
            }
            item(key = "new book") {
                if (editing == NewBook) {
                    BookEditor(
                        initialName = "",
                        viewportHeight = { gridState.layoutInfo.viewportSize.height },
                        initialColor = suggestedColor(books),
                        failed = failed,
                        onConfirm = { name, color, onDone ->
                            failed = false
                            onCreateBook(name, color.toArgb()) { ok ->
                                if (ok) editing = null else failed = true
                                onDone()
                            }
                        },
                        onCancel = {
                            failed = false
                            editing = null
                        },
                    )
                } else {
                    NewBookTile(
                        onClick = {
                            failed = false
                            editing = NewBook
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: BookSort, onSortChange: (BookSort) -> Unit) {
    val colors = paperColors()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort_books),
                tint = colors.accent,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BookSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(
                                when (option) {
                                    BookSort.ALPHABETICAL -> R.string.sort_alphabetical
                                    BookSort.LAST_UPDATED -> R.string.sort_last_updated
                                },
                            ),
                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

private val CoverShape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp, topStart = 3.dp, bottomStart = 3.dp)

/** The name on a cover. Shown by [BookCover] and edited by [BookEditor], so the two look the same. */
@Composable
private fun coverTitleStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(
        fontFamily = PoemFont,
        fontWeight = FontWeight.SemiBold,
        color = CoverTextColor,
        textAlign = TextAlign.Center,
    )

private val CoverTitlePadding = Modifier.padding(start = 26.dp, end = 14.dp)

/** A book's cover in [color], with its spine; [content] goes on top. */
@Composable
private fun CoverBox(color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(color, CoverShape),
    ) {
        // The spine.
        Box(
            Modifier
                .width(14.dp)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)),
        )
        content()
    }
}

@Composable
private fun BookCover(book: Book, onClick: () -> Unit, onEdit: () -> Unit) {
    val colors = paperColors()
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CoverBox(
            color = coverColor(book),
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
            ),
        ) {
            Text(
                text = book.name,
                style = coverTitleStyle(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(CoverTitlePadding),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount),
            style = MaterialTheme.typography.bodySmall,
            color = colors.faintInk,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NewBookTile(onClick: () -> Unit) {
    val colors = paperColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .border(2.dp, colors.faintInk.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.new_book),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PoemFont),
                color = colors.faintInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * A book drawn as an outline, like the new book tile, whose name is typed straight into it where
 * it will be shown. Along the bottom, inside the outline, are a cancel button, a circle in the
 * cover colour (tap it to pick another) and a confirm button.
 *
 * Takes focus and the keyboard as soon as it appears, and keeps the whole book in view above the
 * keyboard as the shelf resizes around it.
 *
 * @param viewportHeight the height of the area the book scrolls in, which changes as the keyboard
 * opens.
 * @param nameEditable false for a book whose name can't change (Misc), which then only gets a colour.
 * @param onConfirm called with the name and colour; call its `onDone` argument when finished so
 * the buttons can be used again.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookEditor(
    initialName: String,
    initialColor: Color,
    viewportHeight: () -> Int,
    nameEditable: Boolean = true,
    failed: Boolean,
    onConfirm: (name: String, color: Color, onDone: () -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = paperColors()
    val nameState = rememberTextFieldState(initialName)
    var colorArgb by rememberSaveable { mutableStateOf(initialColor.toArgb()) }
    var pickingColor by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentViewportHeight by rememberUpdatedState(viewportHeight)

    LaunchedEffect(Unit) {
        if (nameEditable) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        // The shelf shrinks as the keyboard slides up; follow it so the whole book stays visible.
        snapshotFlow { currentViewportHeight() }.collectLatest { bringIntoView.bringIntoView() }
    }

    val titleStyle = coverTitleStyle().copy(color = colors.ink)
    val canConfirm = (nameState.text.isNotBlank() || !nameEditable) && !busy
    fun confirm() {
        if (!canConfirm) return
        busy = true
        // The keyboard's Done key can't insert a line break, but a pasted name could hold one.
        onConfirm(nameState.text.toString().replace('\n', ' '), Color(colorArgb)) { busy = false }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .bringIntoViewRequester(bringIntoView)
            .border(2.dp, colors.faintInk.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
    ) {
        if (!nameEditable) {
            Text(
                text = initialName,
                style = titleStyle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(CoverTitlePadding),
            )
        } else {
            BasicTextField(
                state = nameState,
                textStyle = titleStyle,
                cursorBrush = SolidColor(colors.ink),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = KeyboardActionHandler { confirm() },
                decorator = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (nameState.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.book_name),
                                style = titleStyle.copy(color = colors.faintInk),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(CoverTitlePadding)
                    .focusRequester(focusRequester),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(
                    text = stringResource(R.string.new_book_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.cancel),
                        tint = colors.ink,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .clickable(onClickLabel = stringResource(R.string.choose_colour)) { pickingColor = true },
                )
                IconButton(onClick = ::confirm, enabled = canConfirm) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.done),
                        tint = colors.ink.copy(alpha = if (canConfirm) 1f else 0.35f),
                    )
                }
            }
        }
    }

    if (pickingColor) {
        ColorPickerSheet(
            selected = Color(colorArgb),
            onSelect = { colorArgb = it.toArgb() },
            onDismiss = { pickingColor = false },
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ShelfPreview() {
    PoetryBookTheme {
        ShelfScreen(
            books = listOf(
                Book("a", "Love poems", false, 12),
                Book("b", "Winter", false, 1),
                Book("c", "Misc", true, 0),
            ),
            sort = BookSort.ALPHABETICAL,
            onSortChange = {},
            onOpenBook = {},
            onCreateBook = { _, _, _ -> },
            onEditBook = { _, _, _, _ -> },
            onOpenSettings = {},
        )
    }
}
