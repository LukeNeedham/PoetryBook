package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lukeneedham.poetrybook.R
import com.lukeneedham.poetrybook.ui.theme.PoemFont
import com.lukeneedham.poetrybook.ui.theme.PoetryBookTheme
import com.lukeneedham.poetrybook.ui.theme.paperColors

/**
 * The last page of the book, always. Starting a poem here turns this page into that poem and
 * leaves a fresh one of these after it.
 */
@Composable
fun NewPoemPage(
    onNewPoem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = paperColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.new_poem_page_hint),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = PoemFont),
            color = colors.faintInk,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onNewPoem,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.new_poem))
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun NewPoemPagePreview() {
    PoetryBookTheme { NewPoemPage(onNewPoem = {}) }
}
