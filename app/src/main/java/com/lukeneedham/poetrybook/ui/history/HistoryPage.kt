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
import com.lukeneedham.poetrybook.data.HistoryList
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
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.ui.theme.AppTheme
import com.lukeneedham.poetrybook.data.PoemVersion
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.paperColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The versions of a poem, newest first, as pages to swipe through: newer to the left, older to the
 * right. Opens on the one saved at [createdAt]. Nothing can be edited or deleted here; the text can
 * be selected and copied. The toolbar opens the history list to jump to any version, the notepad
 * (the poem's, the same for every version) and a toggle that marks how the shown version differs
 * from the current poem.
 */
@Composable
fun HistoryPage(
    poemId: String,
    createdAt: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<HistoryViewModel>(parameters = { parametersOf(poemId) })

    // Normally the book page has done this already, and the cached comparisons are reused.
    LaunchedEffect(poemId) { viewModel.trackHistory() }
    // The notes belong to the poem, so they are the same here as beside any other version.
    LaunchedEffect(poemId) { viewModel.loadNotes() }

    HistoryPageContent(
        versions = viewModel.versions,
        createdAt = createdAt,
        notes = viewModel.loadedNotes(),
        poemId = poemId,
        currentPoem = viewModel.currentPoem,
        showChanges = viewModel.showHistoryChanges,
        onShowChangesChange = viewModel::changeShowHistoryChanges,
        history = viewModel.loadedHistory(),
        onLoadMoreHistory = viewModel::loadMoreHistory,
        onSaveNotes = viewModel::saveNotes,
        onBack = onBack,
        modifier = modifier,
    )
}
