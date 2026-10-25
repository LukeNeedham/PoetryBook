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

/** A settings subpage with a back button and [title]. */
@Composable
internal fun BinScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            content()
        }
    }
}

@Composable
internal fun DeleteForeverDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_forever)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_forever)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
