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
import com.lukeneedham.poetrybook.ui.book.PoemBookViewModel
import com.lukeneedham.poetrybook.ui.book.formatTimestamp
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import androidx.compose.ui.tooling.preview.Preview
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.data.BinBook
import com.lukeneedham.poetrybook.data.BinPoem

/** Preview of a deleted poem, with buttons to put it back where it was or to wipe it. */
@Composable
fun BinPoemPage(viewModel: PoemBookViewModel, poemId: String, onBack: () -> Unit) {
    val poem = viewModel.binPoems.firstOrNull { it.id == poemId }
    // Once restored or wiped it is no longer here.
    LaunchedEffect(poem == null) { if (poem == null) onBack() }
    if (poem == null) return

    BinPoemPageContent(
        poem = poem,
        onBack = onBack,
        onRestore = { viewModel.restorePoem(poem.id) },
        onDeleteForever = { onDeleteForever() },
    )
}

@Composable
internal fun BinPoemPageContent(poem: BinPoem, onBack: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    BinScaffold(title = poem.title.ifBlank { stringResource(R.string.untitled) }, onBack = onBack) {
        Text(
            text = stringResource(R.string.bin_poem_from, poem.bookName) + " · " + formatTimestamp(poem.deletedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )
        Button(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp),
        ) { Text(stringResource(R.string.restore)) }
        OutlinedButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp),
        ) { Text(stringResource(R.string.delete_forever)) }

        SelectionContainer {
            Text(
                text = poem.body.ifBlank { stringResource(R.string.empty_poem_hint) },
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PoemFont),
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 24.dp, bottom = 24.dp),
            )
        }
    }

    if (confirmingDelete) {
        DeleteForeverDialog(
            message = stringResource(R.string.delete_forever_poem_message),
            onConfirm = {
                confirmingDelete = false
                onDeleteForever()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}


@Preview(showBackground = true, heightDp = 640)
@Composable
private fun Preview() {
    PoetryBookTheme {
        BinPoemPageContent(
            poem = BinPoem("p", "Evening", "The light goes slowly\nover the roofs", "Love poems", deletedAt = 1_700_100_000_000),
            onBack = {},
            onRestore = {},
            onDeleteForever = {},
        )
    }
}
