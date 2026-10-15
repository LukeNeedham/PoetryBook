package com.lukeneedham.poetrybook.ui.shelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Book
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors
import kotlinx.coroutines.launch

/** The colour of the title on every cover. */
internal val CoverTextColor = Color(0xFFF4EBD8)

/**
 * The cover colours to choose from: muted cloth tones in the same family as the app's paper and
 * accent, all dark enough for the cream title to read on them.
 */
internal val BookPalette = listOf(
    Color(0xFF6B3F36), // oxblood
    Color(0xFF8B4A2B), // the app's accent brown
    Color(0xFF6E5A35), // ochre
    Color(0xFF4E5E34), // olive
    Color(0xFF3F5B52), // pine
    Color(0xFF2F5D62), // teal
    Color(0xFF35506E), // denim
    Color(0xFF3B4A7A), // indigo
    Color(0xFF5B3F6B), // plum
    Color(0xFF7A3340), // burgundy
    Color(0xFF7A4E5F), // mauve
    Color(0xFF4B4B4B), // graphite
)

/** A book that was never given a colour keeps the one its name has always given it. */
private fun defaultColor(name: String): Color = BookPalette[Math.floorMod(name.lowercase().hashCode(), BookPalette.size)]

/** The cover colour of [book]. */
internal fun coverColor(book: Book): Color = book.color?.let(::Color) ?: defaultColor(book.name)

/** The colour to suggest for a new book: the palette colour the fewest [books] already wear. */
internal fun suggestedColor(books: List<Book>): Color {
    val used = books.map(::coverColor)
    return BookPalette.minByOrNull { colour -> used.count { it == colour } } ?: BookPalette.first()
}

/** A bottom sheet with every [BookPalette] colour as a circle; tapping one chooses it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorPickerSheet(
    selected: Color,
    onSelect: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = paperColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.paper,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.book_colour),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PoemFont, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(BookPalette) { colour ->
                    val isSelected = colour == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colour)
                            .then(
                                if (isSelected) Modifier.border(BorderStroke(3.dp, colors.accent), CircleShape) else Modifier,
                            )
                            .clickable {
                                onSelect(colour)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            },
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = CoverTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
