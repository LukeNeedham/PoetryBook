package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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

/** Height of the toolbar's buttons; pages leave this much room above their title. */
internal val BookToolbarHeight = 48.dp

/**
 * The buttons along the top of the book: a ribbon for the contents on the left and a pencil to
 * edit the current poem on the right. It stays put while pages turn underneath it.
 */
@Composable
internal fun BookToolbar(
    canEdit: Boolean,
    editing: Boolean,
    onOpenContents: () -> Unit,
    onToggleEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = paperColors().accent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .height(BookToolbarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenContents) {
            Icon(
                painter = painterResource(R.drawable.ic_ribbon),
                contentDescription = stringResource(R.string.contents),
                tint = tint,
            )
        }
        Box(Modifier.weight(1f))
        // Only poems can be edited; the page for adding one has nothing to edit yet.
        if (canEdit) {
            IconButton(onClick = onToggleEditing) {
                Icon(
                    painter = painterResource(if (editing) R.drawable.ic_check else R.drawable.ic_pencil),
                    contentDescription = stringResource(if (editing) R.string.done else R.string.edit),
                    tint = tint,
                )
            }
        }
    }
}
