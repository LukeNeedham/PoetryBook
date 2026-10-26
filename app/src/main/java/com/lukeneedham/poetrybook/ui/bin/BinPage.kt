package com.lukeneedham.poetrybook.ui.bin

import com.lukeneedham.poetrybook.ui.appViewModel
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

/**
 * The bin: deleted books, then deleted poems. Tap one to preview it and restore it or delete it
 * for good. There is deliberately no way to empty the whole bin at once.
 */
@Composable
fun BinPage(
    onBack: () -> Unit,
    onOpenBook: (bookId: String) -> Unit,
    onOpenPoem: (poemId: String) -> Unit,
) {
    val viewModel = appViewModel { BinViewModel(it) }
    BinPageContent(viewModel.binBooks, viewModel.binPoems, onBack, onOpenBook, onOpenPoem)
}
