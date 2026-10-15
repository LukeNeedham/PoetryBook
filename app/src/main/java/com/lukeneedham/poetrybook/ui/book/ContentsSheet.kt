package com.lukeneedham.poetrybook.ui.book

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.TitleFont

/**
 * The contents page: a sheet that drops down from the top of the screen and lists every poem.
 * Tapping a poem calls [onSelect]; tapping the title calls [onSelectContentsPage]; new poems are started on the book's last page, not here.
 * Pressing and holding a poem lets you drag it to another place in the list, which calls
 * [onMove]; the blank poem at the end can't be moved and nothing can be dragged below it.
 * The handle at the bottom can be dragged up to close the sheet.
 */
@Composable
fun ContentsSheet(
    visible: Boolean,
    poems: List<Poem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onSelectContentsPage: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.75f

        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight),
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.contents),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont),
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onSelectContentsPage)
                                .padding(vertical = 8.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp))

                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0),
                    )
                    val reorder = rememberReorderState(listState, movableCount = (poems.size - 1).coerceAtLeast(0), onMove = onMove)
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                        itemsIndexed(poems, key = { _, poem -> poem.id }) { index, poem ->
                            val dragged = reorder.draggedKey == poem.id
                            ContentsRow(
                                number = index + 1,
                                poem = poem,
                                current = index == currentIndex,
                                onClick = { onSelect(index) },
                                modifier = Modifier
                                    .then(if (dragged) Modifier else Modifier.animateItem())
                                    .reorderable(reorder, key = poem.id, enabled = index < poems.size - 1)
                                    .then(
                                        if (dragged) {
                                            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }

                    DismissHandle(onDismiss)
                }
            }
        }
    }
}

@Composable
internal fun ContentsRow(
    number: Int,
    poem: Poem,
    current: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    currentColor: Color = MaterialTheme.colorScheme.primary,
    numberColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = poem.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = PoemFont,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (poem.title.isBlank()) FontStyle.Italic else FontStyle.Normal,
            ),
            color = if (current) currentColor else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PoemFont),
            color = numberColor,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** A grab handle; dragging it upwards (or tapping it) closes the sheet. */
@Composable
private fun DismissHandle(onDismiss: () -> Unit) {
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onDismiss)
            .draggable(
                state = rememberDraggableState { dragged += it },
                orientation = Orientation.Vertical,
                onDragStarted = { dragged = 0f },
                onDragStopped = { velocity ->
                    if (dragged < -24f || velocity < -400f) onDismiss()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
    }
}
