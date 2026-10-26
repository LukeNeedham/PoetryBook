package com.lukeneedham.poetrybook.ui.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.ln
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.lukeneedham.poetrybook.data.AppSettings
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.data.BookSort
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.TitleFont
import com.lukeneedham.poetrybook.ui.theme.AppTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/** Stands for the book being created in [ShelfPageContent]'s `editing` state; no real document id is empty. */
private const val NewBook = ""

/**
 * The bookshelf: every book as a cover. Tap one to read it.
 *
 * The last tile starts a new book: its cover becomes editable right there, name and colour. The
 * long press on a cover does the same for an existing book, which stays where it is.
 *
 * @param sort how [books] are ordered, which the caller does; [onSortChange] is called when the user picks another.
 * @param onCreateBook creates a book with the given name and cover colour (ARGB); reports whether that worked.
 * @param onDeleteBook moves a book and all its poems to the bin; reports whether that worked.
 * @param onEditBook renames a book and gives it a cover colour (ARGB); reports whether that worked.
 */
@Composable
internal fun ShelfPageContent(
    books: List<Book>,
    sort: BookSort,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onSortChange: (BookSort) -> Unit,
    onOpenBook: (Book) -> Unit,
    onCreateBook: (name: String, color: Int, onDone: (Boolean) -> Unit) -> Unit,
    onEditBook: (book: Book, name: String, color: Int, onDone: (Boolean) -> Unit) -> Unit,
    onDeleteBook: (book: Book, onDone: (Boolean) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    // The id of the book being edited, NewBook for the one being created, or null.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val currentColumns = rememberUpdatedState(columns)
    val currentOnColumnsChange = rememberUpdatedState(onColumnsChange)
    var deleting by remember { mutableStateOf<Book?>(null) }

    // Going back from the editor drops what was typed.
    BackHandler(enabled = editing != null) {
        failed = false
        editing = null
    }

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
            IconButton(onClick = onOpenSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.search),
                    tint = colors.accent,
                )
            }
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
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize().pinchToChangeColumns(currentColumns, currentOnColumnsChange),
        ) {
            items(books, key = { it.id }) { book ->
                if (editing == book.id) {
                    BookEditor(
                        initialName = book.name,
                        placeholder = stringResource(R.string.book_name),
                        viewportHeight = { gridState.layoutInfo.viewportSize.height },
                        initialColor = coverColor(book),
                        onDelete = { deleting = book },
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
                            editing = book.id
                        },
                    )
                }
            }
            item(key = "new book") {
                if (editing == NewBook) {
                    BookEditor(
                        initialName = "",
                        placeholder = stringResource(R.string.new_book),
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

    deleting?.let { book ->
        DeleteBookDialog(
            book = book,
            onConfirm = {
                deleting = null
                failed = false
                onDeleteBook(book) { ok -> if (ok) editing = null else failed = true }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun DeleteBookDialog(book: Book, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_book_title, book.name)) },
        text = { Text(pluralStringResource(R.plurals.delete_book_message, book.poemCount, book.poemCount)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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

/** Where the label sits in the outlined book. */
private val NewBookLabelPadding = 16.dp

/** Every book is this much taller than it is wide: 1.3 tall for 1 wide. */
private const val BookHeightPerWidth = 1.3f

/** The width of a book over its height, which is what `aspectRatio` takes. */
private const val BookAspectRatio = 1f / BookHeightPerWidth

/** The width a cover is drawn at with the default two books to a row, which its sizes are written for. */
private val ReferenceCoverWidth = 150.dp

/** How much bigger or smaller than the shelf's usual cover one [width] wide is, which its text and padding follow. */
private fun coverScale(width: Dp): Float = (width / ReferenceCoverWidth).coerceIn(0.2f, 3f)

private fun TextStyle.scaledBy(scale: Float): TextStyle =
    copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/** Where a cover's name sits, clear of the spine. */
private fun coverTitlePadding(scale: Float) = Modifier.padding(start = 26.dp * scale, end = 14.dp * scale)

/** Each step of the pinch, in how much the fingers move apart or together, changes the books per row by one. */
private const val PinchStepZoom = 1.35f

/**
 * Pinching the shelf changes how many books are in a row: spreading the fingers makes the books
 * bigger (fewer per row), pinching makes them smaller. A single pinch can go through several
 * levels, one for every [PinchStepZoom] of zoom, and back again without lifting the fingers.
 * A single finger is left alone, so the shelf still scrolls.
 */
private fun Modifier.pinchToChangeColumns(
    columns: State<Int>,
    onColumnsChange: State<(Int) -> Unit>,
): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startColumns = columns.value
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    val steps = (ln(zoom) / ln(PinchStepZoom)).toInt()
                    val target = (startColumns - steps).coerceIn(AppSettings.MIN_SHELF_COLUMNS, AppSettings.MAX_SHELF_COLUMNS)
                    if (target != columns.value) onColumnsChange.value(target)
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * A book's cover in [color], with its spine; [content] goes on top, given how much bigger or
 * smaller than the shelf's usual cover this one is drawn, so what's on it can be sized to match.
 */
@Composable
private fun CoverBox(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(scale: Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(BookAspectRatio)
            .background(color, CoverShape),
    ) {
        val scale = coverScale(maxWidth)
        // The spine.
        Box(
            Modifier
                .width(14.dp * scale)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)),
        )
        content(scale)
    }
}

/**
 * A book as the shelf shows it: its cover with the name centred on it and, if [showCount], how
 * many poems it holds. Everything scales with the width the cover is given, so a small one in a
 * dialog looks like the shelf's, only smaller.
 */
@Composable
internal fun BookCoverFace(book: Book, modifier: Modifier = Modifier, showCount: Boolean = true) {
    CoverBox(color = coverColor(book), modifier = modifier) { scale ->
        val padding = coverTitlePadding(scale)
        Text(
            text = book.name,
            style = coverTitleStyle().scaledBy(scale),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .then(padding),
        )
        if (showCount) {
            val countStyle = MaterialTheme.typography.bodySmall
            Text(
                text = pluralStringResource(R.plurals.poem_count, book.poemCount, book.poemCount),
                style = countStyle.scaledBy(scale),
                color = CoverTextColor.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(padding)
                    .padding(bottom = 12.dp * scale),
            )
        }
    }
}

@Composable
private fun BookCover(book: Book, onClick: () -> Unit, onEdit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BookCoverFace(
            book = book,
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEdit()
                },
            ),
        )
    }
}

/** The "New book" label on the empty outlined book. The editor keeps showing it, in this style, until a name is typed. */
@Composable
private fun newBookLabelStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(
        fontFamily = PoemFont,
        color = paperColors().faintInk,
        textAlign = TextAlign.Center,
    )

@Composable
private fun NewBookTile(onClick: () -> Unit) {
    val colors = paperColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BookAspectRatio)
                .border(2.dp, colors.faintInk.copy(alpha = 0.5f), CoverShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val scale = coverScale(maxWidth)
            Text(
                text = stringResource(R.string.new_book),
                style = newBookLabelStyle().scaledBy(scale),
                modifier = Modifier.padding(NewBookLabelPadding * scale),
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
 * @param placeholder shown, exactly as the new book tile shows its label, while no name is typed.
 * @param viewportHeight the height of the area the book scrolls in, which changes as the keyboard
 * opens.
 * @param nameEditable false for a book whose name can't change (Misc), which then only gets a colour.
 * @param onDelete if not null, a bin in the corner of the book calls it.
 * @param onConfirm called with the name and colour; call its `onDone` argument when finished so
 * the buttons can be used again.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BookEditor(
    initialName: String,
    initialColor: Color,
    placeholder: String,
    viewportHeight: () -> Int,
    nameEditable: Boolean = true,
    onDelete: (() -> Unit)? = null,
    failed: Boolean,
    onConfirm: (name: String, color: Color, onDone: () -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = paperColors()
    // Plain `remember`, not saveable: saved state is kept per shelf item, so a saveable editor
    // would bring back the text of an earlier edit of the same book (and a rename that gives a
    // book an id it once had would do the same). Every edit starts from the book as it is now.
    val nameState = remember { TextFieldState(initialName) }
    var colorArgb by remember { mutableStateOf(initialColor.toArgb()) }
    var pickingColor by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentViewportHeight by rememberUpdatedState(viewportHeight)
    var bookSize by remember { mutableStateOf(IntSize.Zero) }
    val marginBelow = with(LocalDensity.current) { 10.dp.toPx() }

    LaunchedEffect(Unit) {
        if (nameEditable) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        // The shelf shrinks as the keyboard slides up; follow it so the whole book stays visible.
        snapshotFlow { currentViewportHeight() }.collectLatest {
            // A little past the bottom of the book, so it doesn't sit hard against the keyboard.
            bringIntoView.bringIntoView(Rect(0f, 0f, bookSize.width.toFloat(), bookSize.height + marginBelow))
        }
    }

    val canConfirm = (nameState.text.isNotBlank() || !nameEditable) && !busy
    fun confirm() {
        if (!canConfirm) return
        busy = true
        // The keyboard's Done key can't insert a line break, but a pasted name could hold one.
        onConfirm(nameState.text.toString().replace('\n', ' '), Color(colorArgb)) { busy = false }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BookAspectRatio)
            .onSizeChanged { bookSize = it }
            .bringIntoViewRequester(bringIntoView)
            .border(2.dp, colors.faintInk.copy(alpha = 0.5f), CoverShape),
    ) {
        // The name is the same size here as it will be on the finished cover, whatever the zoom.
        val scale = coverScale(maxWidth)
        val titleStyle = coverTitleStyle().copy(color = colors.ink).scaledBy(scale)
        val titlePadding = coverTitlePadding(scale)
        if (!nameEditable) {
            Text(
                text = initialName,
                style = titleStyle,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(titlePadding),
            )
        } else {
            if (nameState.text.isEmpty()) {
                // Not part of the field, so it sits exactly where the tile's label does.
                Text(
                    text = placeholder,
                    style = newBookLabelStyle().scaledBy(scale),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(NewBookLabelPadding * scale),
                )
            }
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
                    Box(contentAlignment = Alignment.Center) { inner() }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(titlePadding)
                    .focusRequester(focusRequester),
            )
        }

        // The controls shrink with the book, but not below something that can still be tapped.
        val buttonSize = (48.dp * scale).coerceAtLeast(22.dp)
        val iconSize = (24.dp * scale).coerceAtLeast(14.dp)
        val circleSize = (32.dp * scale).coerceAtLeast(16.dp)

        if (onDelete != null) {
            EditorButton(
                painter = painterResource(R.drawable.ic_trash),
                description = stringResource(R.string.delete_book),
                tint = colors.ink,
                buttonSize = buttonSize,
                iconSize = iconSize,
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 4.dp * scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (failed) {
                Text(
                    text = stringResource(R.string.new_book_failed),
                    style = MaterialTheme.typography.bodySmall.scaledBy(scale),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // On a book too narrow for all three on one line, the controls wrap onto more lines.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                EditorButton(
                    painter = painterResource(R.drawable.ic_close),
                    description = stringResource(R.string.cancel),
                    tint = colors.ink,
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    onClick = onCancel,
                )
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .wrapContentSize()
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(Color(colorArgb))
                        .clickable(onClickLabel = stringResource(R.string.choose_colour)) { pickingColor = true },
                )
                EditorButton(
                    painter = painterResource(R.drawable.ic_check),
                    description = stringResource(R.string.done),
                    tint = colors.ink.copy(alpha = if (canConfirm) 1f else 0.35f),
                    buttonSize = buttonSize,
                    iconSize = iconSize,
                    enabled = canConfirm,
                    onClick = ::confirm,
                )
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

/** An icon button for the book editor, [buttonSize] square with an [iconSize] icon, which can be smaller than the usual 48dp. */
@Composable
private fun EditorButton(
    painter: Painter,
    description: String,
    tint: Color,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painter, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    AppTheme {
        ShelfPageContent(
            books = listOf(
                Book("a", "Love poems", 12),
                Book("b", "Winter", 1),
                Book("c", "Misc", 0),
            ),
            sort = BookSort.ALPHABETICAL,
            columns = 2,
            onColumnsChange = {},
            onSortChange = {},
            onOpenBook = {},
            onDeleteBook = { _, _ -> },
            onCreateBook = { _, _, _ -> },
            onEditBook = { _, _, _, _ -> },
            onOpenSettings = {},
            onOpenSearch = {},
        )
    }
}
