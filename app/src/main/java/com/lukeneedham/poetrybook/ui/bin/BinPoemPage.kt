package com.lukeneedham.poetrybook.ui.bin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.ui.theme.AppTheme
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Preview of a deleted poem, with buttons to put it back where it was or to wipe it. */
@Composable
fun BinPoemPage(poemId: String, onBack: () -> Unit) {
    val viewModel = koinViewModel<BinPoemViewModel>(parameters = { parametersOf(poemId) })
    // Until the bin has been read there is nothing to show, and nothing to leave
    val binPoems = viewModel.binPoems ?: return
    val poem = binPoems.firstOrNull { it.id == poemId }
    // Once restored or wiped it is no longer here.
    LaunchedEffect(poem == null) { if (poem == null) onBack() }
    if (poem == null) return

    BinPoemPageContent(
        poem = poem,
        onBack = onBack,
        onRestore = viewModel::restorePoem,
        onDeleteForever = viewModel::deletePoemForever,
    )
}
