package com.lukeneedham.poetrybook.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import com.lukeneedham.poetrybook.data.DiffLine
import com.lukeneedham.poetrybook.data.LineChange
import com.lukeneedham.poetrybook.data.VersionDiff
import com.lukeneedham.poetrybook.data.diffPoems
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.lukeneedham.poetrybook.data.Poem
import com.lukeneedham.poetrybook.data.differingRanges
import com.lukeneedham.poetrybook.ui.book.NotepadSheet
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import com.lukeneedham.poetrybook.ui.book.HistorySheet
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * The versions of a poem, newest first, as pages to swipe through: newer to the left, older to the
 * right. Opens on the one saved at [createdAt]. Nothing can be edited or deleted here; the text can
 * be selected and copied. The toolbar opens the history list to jump to any version, the notepad
 * (the poem's, the same for every version) and a toggle that marks how the shown version differs
 * from the current poem.
 */
@Composable
fun HistoryScreen(
    viewModel: PoemBookViewModel,
    poemId: String,
    createdAt: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    val versionsFlow = remember(poemId) { viewModel.history(poemId) }
    val versions by versionsFlow.collectAsState<List<PoemVersion>, List<PoemVersion>?>(initial = null)
    val loaded = versions
    val startIndex = loaded?.indexOfFirst { it.createdAt == createdAt }

    when {
        loaded == null -> Box(modifier.fillMaxSize().background(colors.paper))
        startIndex == null || startIndex < 0 -> Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.paper)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.back),
                        tint = colors.accent,
                    )
                }
            }
            HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))
            Text(
                text = stringResource(R.string.version_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.faintInk,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> HistoryPager(viewModel, poemId, loaded, startIndex, onBack, modifier)
    }
}

@Composable
private fun HistoryPager(
    viewModel: PoemBookViewModel,
    poemId: String,
    versions: List<PoemVersion>,
    startIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    val pagerState = rememberPagerState(initialPage = startIndex) { versions.size }
    val scope = rememberCoroutineScope()
    var notepadOpen by rememberSaveable { mutableStateOf(false) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    // The notes belong to the poem, so they are the same here as beside any other version.
    LaunchedEffect(poemId) { viewModel.loadNotes(poemId) }
    val notes = viewModel.loadedNotes(poemId)
    val currentPoem = viewModel.poems.firstOrNull { it.id == poemId }
    val shown = versions.getOrNull(pagerState.currentPage)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.back),
                    tint = colors.accent,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (shown != null) {
                    Text(
                        text = formatTimestamp(shown.createdAt),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (pagerState.currentPage == 0) {
                            stringResource(R.string.history_current)
                        } else {
                            stringResource(R.string.history_position, pagerState.currentPage + 1, versions.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.faintInk,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = { historyOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = stringResource(R.string.history),
                    tint = colors.accent,
                )
            }
            if (currentPoem != null) {
                val showing = viewModel.showHistoryChanges
                IconButton(onClick = { viewModel.changeShowHistoryChanges(!showing) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_compare),
                        contentDescription = stringResource(
                            if (showing) R.string.history_hide_changes else R.string.history_show_changes,
                        ),
                        tint = colors.accent.copy(alpha = if (showing) 1f else 0.45f),
                    )
                }
            }
            IconButton(onClick = { notepadOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_notepad),
                    contentDescription = stringResource(R.string.notepad),
                    tint = colors.accent,
                )
            }
        }
        HorizontalDivider(color = colors.faintInk.copy(alpha = 0.25f))

        HorizontalPager(
            state = pagerState,
            key = { versions.getOrNull(it)?.createdAt ?: it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            versions.getOrNull(page)?.let { version ->
                VersionPage(version, compareTo = currentPoem, showChanges = viewModel.showHistoryChanges)
            }
        }
    }

    if (historyOpen) {
        HistorySheet(
            versions = remember(poemId) { viewModel.history(poemId) },
            onSelect = { version ->
                historyOpen = false
                val index = versions.indexOfFirst { it.createdAt == version.createdAt }
                if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
            },
            onDismiss = { historyOpen = false },
        )
    }

    if (notepadOpen && notes != null) {
        NotepadSheet(
            viewModel = viewModel,
            poemId = poemId,
            notes = notes,
            onDismiss = { notepadOpen = false },
        )
    }
}

@Composable
private fun VersionPage(version: PoemVersion, compareTo: Poem?, showChanges: Boolean) {
    val colors = paperColors()
    val titleStyle = TextStyle(
        fontFamily = PoemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        textAlign = TextAlign.Center,
        color = colors.ink,
    )
    val bodyStyle = TextStyle(fontFamily = PoemFont, fontSize = 19.sp, lineHeight = 30.sp, color = colors.ink)

    // How this version differs from the current poem. Unknown if the current poem isn't at hand.
    val titleDiff = remember(version, compareTo) { compareTo?.let { differingRanges(version.title, it.title) }.orEmpty() }
    val diff = remember(version, compareTo) { compareTo?.let { diffPoems(version.body, it.body) } }
    val same = compareTo != null && diff != null && diff.isSame && version.title == compareTo.title
    val markWords = SpanStyle(background = EditedColor.copy(alpha = 0.5f))

    SelectionContainer(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.history_read_only),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = colors.faintInk,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            val comparison = when {
                compareTo == null -> null
                same -> R.string.history_same_as_current
                showChanges -> R.string.history_differences
                else -> null
            }
            if (comparison != null) {
                Text(
                    text = stringResource(comparison),
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = colors.faintInk,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
            Text(
                text = if (version.title.isBlank()) {
                    AnnotatedString(stringResource(R.string.untitled))
                } else {
                    buildAnnotatedString {
                        append(version.title)
                        if (showChanges) titleDiff.forEach { addStyle(markWords, it.first, it.last + 1) }
                    }
                },
                style = if (version.title.isBlank()) {
                    titleStyle.copy(color = colors.faintInk, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)
                } else {
                    titleStyle
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            if (showChanges && diff != null) {
                DiffBody(diff, bodyStyle)
            } else {
                Text(text = version.body, style = bodyStyle)
            }
        }
    }
}

private val EditedColor = Color(0xFFE0A030)
private val RemovedColor = Color(0xFFD05050)
private val MovedColor = Color(0xFF4A86C8)
private val AddedColor = Color(0xFF4C9A5F)

/**
 * The older version stanza by stanza and line by line, each marked with what became of it:
 * a bar beside a stanza that moved, an arrow and a note beside a line that moved, a tint and
 * highlighted words on an edited line, a red tint on a line that's gone, and a green note
 * wherever lines were added since.
 */
@Composable
private fun DiffBody(diff: VersionDiff, bodyStyle: TextStyle) {
    val colors = paperColors()
    val noteStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = PoemFont)
    Column(Modifier.fillMaxWidth()) {
        if (diff.addedAtStart > 0) AddedNote(diff.addedAtStart, noteStyle, Modifier.padding(bottom = 4.dp))
        diff.stanzas.forEachIndexed { stanzaIndex, stanza ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = if (stanzaIndex == 0) 0.dp else 18.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(if (stanza.movedTo != null) MovedColor else Color.Transparent),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (stanza.movedTo != null) {
                        val up = stanza.movedTo - 1 < stanzaIndex
                        Text(
                            text = (if (up) "↑ " else "↓ ") + stringResource(R.string.diff_moved_stanza, stanza.movedTo),
                            style = noteStyle,
                            color = MovedColor,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    stanza.lines.forEachIndexed { lineIndex, line ->
                        DiffLineRow(line, stanzaIndex, lineIndex, diff.currentStanzaCount, bodyStyle, noteStyle)
                        if (line.addedAfter > 0) AddedNote(line.addedAfter, noteStyle, Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(
    line: DiffLine,
    stanzaIndex: Int,
    lineIndex: Int,
    currentStanzaCount: Int,
    bodyStyle: TextStyle,
    noteStyle: TextStyle,
) {
    val tint = when (line.change) {
        LineChange.SAME -> Color.Transparent
        LineChange.MOVED -> MovedColor.copy(alpha = 0.14f)
        LineChange.EDITED -> EditedColor.copy(alpha = 0.16f)
        LineChange.REMOVED -> RemovedColor.copy(alpha = 0.18f)
    }
    val text = buildAnnotatedString {
        append(line.text)
        line.edited.forEach { addStyle(SpanStyle(background = EditedColor.copy(alpha = 0.5f)), it.first, it.last + 1) }
    }
    Column(Modifier.fillMaxWidth().background(tint, RoundedCornerShape(4.dp))) {
        Text(text = text, style = bodyStyle)
        val note = when {
            line.change == LineChange.REMOVED -> stringResource(R.string.diff_removed)
            line.movedTo != null -> {
                val place = line.movedTo
                val up = place.stanza - 1 < stanzaIndex || (place.stanza - 1 == stanzaIndex && place.line - 1 < lineIndex)
                (if (up) "↑ " else "↓ ") + if (currentStanzaCount > 1) {
                    stringResource(R.string.diff_moved_line_in_stanza, place.stanza, place.line)
                } else {
                    stringResource(R.string.diff_moved_line, place.line)
                }
            }
            else -> null
        }
        if (note != null) {
            Text(
                text = note,
                style = noteStyle,
                color = if (line.change == LineChange.REMOVED) RemovedColor else MovedColor,
            )
        }
    }
}

@Composable
private fun AddedNote(count: Int, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(R.plurals.diff_added, count, count),
        style = style.copy(fontStyle = FontStyle.Italic),
        color = AddedColor,
        modifier = modifier,
    )
}
