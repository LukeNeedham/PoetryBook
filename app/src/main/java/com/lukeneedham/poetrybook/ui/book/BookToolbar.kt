package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * The buttons along the top of the book: the bookshelf and a ribbon for the contents on the left,
 * and on the right a bin to delete the current poem and a pencil to edit it.
 *
 * It's a band of its own above the pages, with its own paper, so it never turns with them.
 * Whoever places it deals with the status bar inset.
 */
@Composable
internal fun BookToolbar(
    editing: Boolean,
    canDelete: Boolean,
    onOpenShelf: () -> Unit,
    onOpenContents: () -> Unit,
    onDelete: () -> Unit,
    onToggleEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    Column(modifier = modifier.fillMaxWidth().background(colors.paper)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenShelf) {
                Icon(
                    painter = painterResource(R.drawable.ic_shelf),
                    contentDescription = stringResource(R.string.bookshelf),
                    tint = colors.accent,
                )
            }
            IconButton(onClick = onOpenContents) {
                Icon(
                    painter = painterResource(R.drawable.ic_ribbon),
                    contentDescription = stringResource(R.string.contents),
                    tint = colors.accent,
                )
            }
            Box(Modifier.weight(1f))
            // Greyed out while there is nothing written to delete.
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = stringResource(R.string.delete_poem),
                    tint = colors.accent.copy(alpha = if (canDelete) 1f else 0.35f),
                )
            }
            IconButton(onClick = onToggleEditing) {
                Icon(
                    painter = painterResource(if (editing) R.drawable.ic_check else R.drawable.ic_pencil),
                    contentDescription = stringResource(if (editing) R.string.done else R.string.edit),
                    tint = colors.accent,
                )
            }
        }
        HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))
    }
}
